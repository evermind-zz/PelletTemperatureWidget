#!/bin/bash

# Loop through all matching files in the current directory
# and convert files like app_src_main_whatever_path_to_File.kt
# to app/src/main/whatever/path/to/File.kt
# It creates the dirs and moves the file to the full path

for file in app_*; do
  # Skip if not a regular file
  [[ -f "$file" ]] || continue

  # Strip extension
  extension="${file##*.}"
  filename="${file%.*}"

  # Separate base path and actual file name
  base_path=$(echo "$filename" | sed -E 's|_([^_]+)$| \1|')
  path=$(echo "$base_path" | awk '{gsub(/_/, "/", $1); print $1 "/" $2}')

  # Final full destination path
  full_path="${path}.${extension}"

  # Create destination directory
  dest_dir=$(dirname "$full_path")
  mkdir -p "$dest_dir"

  # Move the file
  mv "$file" "$full_path"

  echo "Moved: $file -> $full_path"
done

