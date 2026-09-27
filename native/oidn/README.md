# Intel Open Image Denoise

`windows-x64/` holds the CPU build of [Intel Open Image Denoise](https://github.com/RenderKit/oidn)
2.5.1, taken unchanged from the official release `oidn-2.5.1.x64.windows.zip`: the four DLLs the
CPU device needs, the OIDN licence (Apache 2.0) and the oneTBB third-party notice.

`render/Denoiser` loads them from here when a render runs with `-Drt.denoise`; `-Drt.oidn=<dir>`
or the `OIDN_DIR` environment variable point it at another copy instead. Nothing is installed
or registered on the machine.

To update, replace the DLLs with the same four files from a newer release's `bin/` folder.
