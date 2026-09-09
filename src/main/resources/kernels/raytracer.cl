// Path tracer kernel: a port of Camera.rayColor / Sphere.hit / Aabb.hit / the material and
// texture classes. The recursion of rayColor becomes a throughput loop, and every random
// draw keeps the order and the distribution the CPU uses so the noise looks the same.

#define LEAF          (-1)
#define STACK_SIZE    64
#define REJECTION_CAP 64

// ---------------------------------------------------------------- random numbers

typedef struct {
    ulong state;
    ulong inc;
} Rng;

inline uint rng_next(Rng* rng) {
    ulong old = rng->state;
    rng->state = old * 6364136223846793005UL + rng->inc;
    uint xorshifted = (uint)(((old >> 18) ^ old) >> 27);
    uint rot = (uint)(old >> 59);
    return (xorshifted >> rot) | (xorshifted << ((32 - rot) & 31));
}

// One independent stream per (pixel, sample), so the image does not depend on how the
// work is split into batches.
inline void rng_init(Rng* rng, ulong seed, ulong pixel, ulong sample) {
    rng->state = 0UL;
    rng->inc = ((pixel * 0x9E3779B97F4A7C15UL + sample) << 1) | 1UL;
    rng_next(rng);
    rng->state += seed + 0x853C49E6748FEA9BUL * (sample + 1UL);
    rng_next(rng);
}

inline float rng_float(Rng* rng) {
    return (float)(rng_next(rng) >> 8) * 0x1.0p-24f;   // [0, 1)
}

inline float rng_range(Rng* rng, float lo, float hi) {
    return rng_float(rng) * (hi - lo) + lo;
}

inline float3 random_in_unit_sphere(Rng* rng) {
    for (int i = 0; i < REJECTION_CAP; i++) {
        float3 p = (float3)(rng_range(rng, -1.0f, 1.0f), rng_range(rng, -1.0f, 1.0f), rng_range(rng, -1.0f, 1.0f));
        if (dot(p, p) < 1.0f) return p;
    }
    return (float3)(0.0f, 0.0f, 1.0f);
}

inline float3 random_on_hemisphere(Rng* rng, float3 normal) {
    float3 p = random_in_unit_sphere(rng);
    float3 onUnitSphere = p / length(p);
    return dot(onUnitSphere, normal) > 0.0f ? onUnitSphere : -onUnitSphere;
}

inline float2 random_in_unit_disc(Rng* rng) {
    for (int i = 0; i < REJECTION_CAP; i++) {
        float2 p = (float2)(rng_range(rng, -1.0f, 1.0f), rng_range(rng, -1.0f, 1.0f));
        if (dot(p, p) < 1.0f) return p;
    }
    return (float2)(0.0f, 0.0f);
}

inline bool near_zero(float3 v) {
    const float s = 1e-8f;
    return fabs(v.x) < s && fabs(v.y) < s && fabs(v.z) < s;
}

// ---------------------------------------------------------------- geometry

typedef struct {
    float3 point;
    float3 normal;
    float t;
    float u;
    float v;
    int material;
    bool frontFace;
} HitRecord;

inline bool aabb_hit(__global const float* nodeBounds, int node, float3 origin, float3 direction,
                     float tMin, float tMax) {
    __global const float* box = nodeBounds + node * 6;
    float o[3] = { origin.x, origin.y, origin.z };
    float d[3] = { direction.x, direction.y, direction.z };

    for (int axis = 0; axis < 3; axis++) {
        float invD = 1.0f / d[axis];
        float t0 = (box[axis] - o[axis]) * invD;
        float t1 = (box[axis + 3] - o[axis]) * invD;

        if (t0 > t1) {
            float tmp = t0;
            t0 = t1;
            t1 = tmp;
        }
        if (t0 > tMin) tMin = t0;
        if (t1 < tMax) tMax = t1;
        if (tMax <= tMin) return false;
    }
    return true;
}

inline bool sphere_hit(__global const float* spheres, __global const int* sphereMaterials, int index,
                       float3 origin, float3 direction, float time, float tMin, float tMax,
                       HitRecord* rec) {
    __global const float* s = spheres + index * 8;
    float3 center = (float3)(s[0], s[1], s[2]) + time * (float3)(s[3], s[4], s[5]);
    float radius = s[6];

    float3 oc = origin - center;
    float a = dot(direction, direction);
    float h = dot(direction, -oc);
    float c = dot(oc, oc) - radius * radius;
    float discriminant = h * h - a * c;
    if (discriminant < 0.0f) return false;

    float sqrtd = sqrt(discriminant);
    float root = (h - sqrtd) / a;
    if (!(root > tMin && root < tMax)) {
        root = (h + sqrtd) / a;
        if (!(root > tMin && root < tMax)) return false;
    }

    float3 hitpoint = origin + root * direction;
    float3 outwardNormal = (hitpoint - center) / radius;

    // Sphere.getSphereUv
    float theta = acos(-outwardNormal.y);
    float phi = atan2(-outwardNormal.z, outwardNormal.x) + M_PI_F;

    rec->point = hitpoint;
    rec->t = root;
    rec->u = phi / (2.0f * M_PI_F);
    rec->v = theta / M_PI_F;
    rec->material = sphereMaterials[index];
    rec->frontFace = dot(direction, outwardNormal) < 0.0f;
    rec->normal = rec->frontFace ? outwardNormal : -outwardNormal;
    return true;
}

// Closest hit over the flattened tree; equivalent to BvhNode.hit narrowing the interval
// as it descends.
inline bool world_hit(__global const float* nodeBounds, __global const int* nodeLinks,
                      __global const float* spheres, __global const int* sphereMaterials,
                      int rootNode, float3 origin, float3 direction, float time,
                      float tMin, float tMax, HitRecord* rec) {
    int stack[STACK_SIZE];
    int sp = 0;
    stack[sp++] = rootNode;

    bool hitAnything = false;
    float closest = tMax;

    while (sp > 0) {
        int node = stack[--sp];
        if (!aabb_hit(nodeBounds, node, origin, direction, tMin, closest)) continue;

        int first = nodeLinks[node * 2];
        int second = nodeLinks[node * 2 + 1];

        if (second == LEAF) {
            HitRecord candidate;
            if (sphere_hit(spheres, sphereMaterials, first, origin, direction, time, tMin, closest, &candidate)) {
                hitAnything = true;
                closest = candidate.t;
                *rec = candidate;
            }
        } else if (sp + 2 <= STACK_SIZE) {
            stack[sp++] = first;
            stack[sp++] = second;
        }
    }
    return hitAnything;
}

// ---------------------------------------------------------------- textures

inline float3 texture_value(__global const int* texI, __global const float* texF,
                            __global const uchar* images, int index, float u, float v, float3 p) {
    for (int hop = 0; hop < 8; hop++) {
        int type = texI[index * 4];

        if (type == 0) {                                  // SolidColorTexture
            __global const float* f = texF + index * 4;
            return (float3)(f[0], f[1], f[2]);
        }
        if (type == 1) {                                  // CheckerTexture
            float inverted = texF[index * 4];
            int xInteger = (int)floor(inverted * p.x);
            int yInteger = (int)floor(inverted * p.y);
            int zInteger = (int)floor(inverted * p.z);
            bool isEven = ((xInteger + yInteger + zInteger) % 2) == 0;
            index = isEven ? texI[index * 4 + 1] : texI[index * 4 + 2];
            continue;
        }
        if (type == 2) {                                  // ImageTexture
            int offset = texI[index * 4 + 1];
            int width = texI[index * 4 + 2];
            int height = texI[index * 4 + 3];

            float uu = clamp(u, 0.0f, 1.0f);
            float vv = 1.0f - clamp(v, 0.0f, 1.0f);       // flip V to image coordinates

            int i = (int)(uu * (float)width);
            int j = (int)(vv * (float)height);
            i = i < 0 ? 0 : (i < width ? i : width - 1);
            j = j < 0 ? 0 : (j < height ? j : height - 1);

            int at = offset + (j * width + i) * 3;
            return (float3)((float)images[at], (float)images[at + 1], (float)images[at + 2]) * (1.0f / 255.0f);
        }
        break;                                            // image that failed to load
    }
    return (float3)(0.0f, 1.0f, 1.0f);
}

// ---------------------------------------------------------------- materials

inline float reflectance(float cosine, float ri) {
    float r0 = (1.0f - ri) / (1.0f + ri);
    r0 = r0 * r0;
    float t = 1.0f - cosine;
    float t2 = t * t;
    return r0 + (1.0f - r0) * (t2 * t2 * t);
}

inline float3 refract_dir(float3 uv, float3 n, float etaiOverEtat) {
    float cosTheta = fmin(dot(-uv, n), 1.0f);
    float3 rOutPerp = (n * cosTheta + uv) * etaiOverEtat;
    float3 rOutParallel = -(n * sqrt(fabs(1.0f - dot(rOutPerp, rOutPerp))));
    return rOutPerp + rOutParallel;
}

inline bool scatter(__global const int* matI, __global const float* matF,
                    __global const int* texI, __global const float* texF,
                    __global const uchar* images,
                    const HitRecord* rec, float3 inDirection, Rng* rng,
                    float3* attenuation, float3* scattered) {
    int type = matI[rec->material * 2];
    int texture = matI[rec->material * 2 + 1];

    if (type == 0) {                                      // Lambertian
        float3 direction = random_on_hemisphere(rng, rec->normal);
        if (near_zero(direction)) direction = rec->normal;
        *attenuation = texture_value(texI, texF, images, texture, rec->u, rec->v, rec->point);
        *scattered = direction;
        return true;
    }

    float3 unitDirection = inDirection / length(inDirection);

    if (type == 1) {                                      // Metal
        float fuzz = matF[rec->material * 2];
        float3 reflected = unitDirection - rec->normal * (2.0f * dot(unitDirection, rec->normal));
        float3 direction = reflected + random_in_unit_sphere(rng) * fuzz;
        if (dot(direction, rec->normal) <= 0.0f) return false;
        *attenuation = texture_value(texI, texF, images, texture, rec->u, rec->v, rec->point);
        *scattered = direction;
        return true;
    }

    // Dielectric
    float ri = matF[rec->material * 2 + 1];
    float refractionRatio = rec->frontFace ? (1.0f / ri) : ri;
    float cosTheta = fmin(dot(-unitDirection, rec->normal), 1.0f);
    float sinTheta = sqrt(1.0f - cosTheta * cosTheta);

    bool cannotRefract = refractionRatio * sinTheta > 1.0f;
    // Short-circuit: the random draw only happens when refraction is possible, as on the CPU.
    bool useReflect = cannotRefract || (reflectance(cosTheta, refractionRatio) > rng_float(rng));

    *attenuation = (float3)(1.0f, 1.0f, 1.0f);
    *scattered = useReflect
        ? unitDirection - rec->normal * (2.0f * dot(unitDirection, rec->normal))
        : refract_dir(unitDirection, rec->normal, refractionRatio);
    return true;
}

// ---------------------------------------------------------------- entry point

__kernel void render(__global const float* cam,
                     __global const float* nodeBounds,
                     __global const int* nodeLinks,
                     __global const float* spheres,
                     __global const int* sphereMaterials,
                     __global const int* matI,
                     __global const float* matF,
                     __global const int* texI,
                     __global const float* texF,
                     __global const uchar* images,
                     __global float* out,
                     const int width,
                     const int height,
                     const int rowOffset,
                     const int samplesPerPixel,
                     const int maxReflectionDepth,
                     const float pixelSamplesScale,
                     const int rootNode,
                     const ulong seed) {
    int gid = get_global_id(0);
    int x = gid % width;
    int y = rowOffset + gid / width;
    if (y >= height) return;

    float3 cameraCenter = (float3)(cam[0], cam[1], cam[2]);
    float3 pixel00      = (float3)(cam[3], cam[4], cam[5]);
    float3 pixelDeltaU  = (float3)(cam[6], cam[7], cam[8]);
    float3 pixelDeltaV  = (float3)(cam[9], cam[10], cam[11]);
    float3 defocusDiscU = (float3)(cam[12], cam[13], cam[14]);
    float3 defocusDiscV = (float3)(cam[15], cam[16], cam[17]);
    float defocusAngle  = cam[18];

    float3 pixelColor = (float3)(0.0f, 0.0f, 0.0f);

    for (int sample = 0; sample < samplesPerPixel; sample++) {
        Rng rng;
        rng_init(&rng, seed, (ulong)(y * width + x), (ulong)sample);

        // Camera.getRay
        float offsetX = rng_float(&rng) - 0.5f;
        float offsetY = rng_float(&rng) - 0.5f;
        float3 pixelSample = pixel00
            + pixelDeltaU * ((float)x + offsetX)
            + pixelDeltaV * ((float)y + offsetY);

        float3 origin = cameraCenter;
        if (defocusAngle > 0.0f) {
            float2 p = random_in_unit_disc(&rng);
            origin = cameraCenter + defocusDiscU * p.x + defocusDiscV * p.y;
        }
        float3 direction = pixelSample - origin;
        float time = rng_float(&rng);

        // Camera.rayColor, unrolled into a throughput loop. A path that never escapes the
        // scene within maxReflectionDepth bounces contributes black, as on the CPU.
        float3 throughput = (float3)(1.0f, 1.0f, 1.0f);
        float3 sampleColor = (float3)(0.0f, 0.0f, 0.0f);

        for (int depth = 0; depth < maxReflectionDepth; depth++) {
            HitRecord rec;
            if (world_hit(nodeBounds, nodeLinks, spheres, sphereMaterials, rootNode,
                          origin, direction, time, 0.001f, INFINITY, &rec)) {
                float3 attenuation;
                float3 scattered;
                if (!scatter(matI, matF, texI, texF, images, &rec, direction, &rng, &attenuation, &scattered)) {
                    break;
                }
                throughput *= attenuation;
                origin = rec.point;
                direction = scattered;
            } else {
                float3 unitDirection = direction / length(direction);
                float alpha = 0.5f * (unitDirection.y + 1.0f);
                float3 background = (float3)(1.0f, 1.0f, 1.0f) * (1.0f - alpha)
                                  + (float3)(0.5f, 0.7f, 1.0f) * alpha;
                sampleColor = throughput * background;
                break;
            }
        }

        pixelColor += sampleColor;
    }

    pixelColor *= pixelSamplesScale;

    int base = (y * width + x) * 3;
    out[base]     = pixelColor.x;
    out[base + 1] = pixelColor.y;
    out[base + 2] = pixelColor.z;
}
