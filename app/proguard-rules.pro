# Glass Home has no reflection of its own. NanoHTTPD reads its MIME types from
# a properties file in its jar, which R8 leaves in place.

# Keep line numbers in crash reports readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
