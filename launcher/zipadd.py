"""zipadd.py <zip> <root_dir> [files...]: add files (relative to root_dir) into zip; all files if none given."""
import sys, os, zipfile
z, root = sys.argv[1], sys.argv[2]
files = sys.argv[3:] or [os.path.relpath(os.path.join(d, f), root) for d, _, fs in os.walk(root) for f in fs]
with zipfile.ZipFile(z, "a", zipfile.ZIP_DEFLATED) as zf:
    for f in files: zf.write(os.path.join(root, f), f.replace(os.sep, "/"))
