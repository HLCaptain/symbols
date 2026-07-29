# Keep the original names of vector backing classes that survive shrinking.
# `-keepnames` still permits shrinking, so absent names remain direct evidence
# that the corresponding vector class was removed.
-keepnames class io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedVector*
