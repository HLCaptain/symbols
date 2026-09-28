# Full sample APK size comparison

All ten profiles built in release and R8-shrunk form at A `20339e8` and B `3aea238`. These builds ran concurrently; their timings are deliberately excluded. [Compact sizes, hashes and contents](SAMPLE_APPS.json).

| Profile | A release | B release | A shrunk | B shrunk | Shrunk change |
| --- | ---: | ---: | ---: | ---: | ---: |
| all | 44,234,216 | 43,849,761 | 22,967,644 | 22,980,545 | +0.06% |
| android-views | 37,143,437 | 36,770,144 | 16,466,747 | 16,457,984 | -0.05% |
| custom-static | 14,244,621 | 13,947,288 | 1,551,929 | 1,582,334 | +1.96% |
| custom-variable | 14,514,447 | 14,233,498 | 1,772,603 | 1,803,012 | +1.72% |
| image-vector-migration | 33,531,119 | 33,154,351 | 16,299,006 | 16,313,031 | +0.09% |
| material-static | 21,141,932 | 20,800,785 | 7,711,960 | 7,747,649 | +0.46% |
| material-variable | 28,856,996 | 28,559,663 | 16,131,536 | 16,145,561 | +0.09% |
| runtime-axes | 28,856,996 | 28,576,047 | 16,229,840 | 16,260,249 | +0.19% |
| shell | 14,155,317 | 13,874,372 | 1,495,397 | 1,509,422 | +0.94% |
| theming | 15,970,778 | 15,673,445 | 3,245,318 | 3,259,343 | +0.43% |

APK bytes include ZIP metadata and signatures. Fonts have exactly the same decoded bytes and paths across both toolchains; TTF compression remains disabled for the renderer’s memory-mapping behavior. The 14,586,584-byte rounded variable font dominates the profiles that include it. R8 does not remove individual glyphs from a used font.

The upgrade alone yields small APK changes rather than automatic font/Compose-asset subsetting. The Android Views profile also exposes a separate dependency problem: it imports a full sample feature just to access two SVG-generated resources. That optimization is evaluated separately as B versus C.
