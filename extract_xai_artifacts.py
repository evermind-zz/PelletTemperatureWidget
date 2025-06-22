import re
import os
import sys

# script that can parse files that are provided by grok. It also features a
# mapping as this format does not include the paths of the file just the
# filename. See mapping = { ... }
# This is the format that grok offers sometims (maybe it is just a glitch) but here you can see the format:
#
# <xaiArtifact artifact_id="7950cd7b-9d07-408a-ab48-7437afa87f04" artifact_version_id="53c06148-b36b-4128-8776-b395e48ed77d" title="Logger.kt" contentType="text/x-kotlin">
# ```kotlin
# whatever is inside here should be placed in the file name provided in title="..." if there is a mapping for Logger.kt -> /path/to/logger/Logger.kt use this file
# ```
# </xArtifact>
#
# <xaiArtifact artifact_id="7950cd7b-9d07-408a-ab48-7437afa87f05" artifact_version_id="53c06148-b36b-4128-8776-b395e48ed77d" title="Baga.kt" contentType="text/x-kotlin">
# ```kotlin
# whatever is inside here should be placed in the file name provided in title="..." if there is a mapping for Baga.kt -> /path/to/baga/Baga.kt use this file
# ```
# </xArtifact>
#
# -> Another format is also supported. It is custom, but grok gets it. Each
#    files has a header with '#' around the filepath. This format works great
#
# ##############
# # a/FileA.kt
# ##############
# ```kotlin
# fun foo() = "bar"
# ```
# ##############
# # a/FileB.kt
# ##############
# fun baz() = "qux"
#
# ##############
# # a/FileC.kt
# ##############
# ```json
# fun foo() = "bar"

mapping = {
    "ContextProvider.kt": "app/src/main/java/com/example/pelletswaterheater/utils/ContextProvider.kt",
    "Logger.kt": "app/src/main/java/com/example/pelletswaterheater/utils/Logger.kt",
    "MetadataUtils.kt": "app/src/main/java/com/example/pelletswaterheater/utils/MetadataUtils.kt",
    "ValueConverter.kt": "app/src/main/java/com/example/pelletswaterheater/utils/ValueConverter.kt",
    "TemperatureViewModelNew.kt": "app/src/main/java/com/example/pelletswaterheater/viewmodel/TemperatureViewModelNew.kt",
    "ValueConverterTest.kt": "app/src/test/java/com/example/pelletswaterheater/utils/ValueConverterTest.kt",
    "TemperatureViewModelNewTest.kt": "app/src/test/java/com/example/pelletswaterheater/viewmodel/TemperatureViewModelNewTest.kt",
    "server.json": "app/src/test/resources/server.json",
    "user.json": "app/src/test/resources/user.json",
}

def write_file(output_path, code):
    directory = os.path.dirname(output_path)
    if directory:
        try:
            os.makedirs(directory, exist_ok=True)
        except Exception as e:
            print(f"[ERROR] Failed to create directory for '{output_path}': {e}")
            return False
    try:
        with open(output_path, "w", encoding="utf-8") as out_file:
            out_file.write(code)
        print(f"[+] Wrote to {output_path}")
        return True
    except Exception as e:
        print(f"[ERROR] Failed to write file '{output_path}': {e}")
        return False

def parse_xai_artifacts(data):
    # Use your existing <xaiArtifact> parsing logic (note you had a small typo in the closing tag)
    pattern = r'<xaiArtifact[^>]*title="([^"]+)"[^>]*>(.*?)</xArtifact>'
    return re.findall(pattern, data, re.DOTALL)

def parse_hash_blocks(data):
    blocks = []
    lines = data.splitlines()

    current_path = None
    current_content = []

    i = 0
    while i < len(lines):
        line = lines[i].strip()

        # Detect block start: ############## + # path + ##############
        if re.match(r"^#{6,}$", line):
            # Look ahead for the path line and another ##############
            if i + 2 < len(lines):
                path_line = lines[i + 1].strip()
                next_delim = lines[i + 2].strip()
                if path_line.startswith("# ") and re.match(r"^#{6,}$", next_delim):
                    # Save current block before starting a new one
                    if current_path and current_content:
                        content = "\n".join(current_content).strip()
                        blocks.append((current_path, content))
                    current_path = path_line[2:].strip()
                    current_content = []
                    i += 3
                    continue
        # Collect content
        if current_path is not None:
            # Ignore code fences like ```kotlin or ```
            if not line.startswith("```"):
                current_content.append(lines[i])

        i += 1

    # Save last block
    if current_path and current_content:
        content = "\n".join(current_content).strip()
        blocks.append((current_path, content))

    return blocks


def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <input_file>")
        sys.exit(1)

    input_file = sys.argv[1]

    try:
        with open(input_file, "r", encoding="utf-8") as f:
            data = f.read()
    except FileNotFoundError:
        print(f"[ERROR] Input file '{input_file}' not found.")
        sys.exit(2)
    except Exception as e:
        print(f"[ERROR] Failed to read input file '{input_file}': {e}")
        sys.exit(3)

    # Parse <xaiArtifact> blocks
    artifact_blocks = parse_xai_artifacts(data)
    if not artifact_blocks:
        print("[WARNING] No <xaiArtifact> entries found in the input file.")

    for title, content in artifact_blocks:
        code_match = re.search(r'```.*?\n(.*?)```', content, re.DOTALL)
        if not code_match:
            print(f"[!] No code block found in artifact: '{title}'. Skipping.")
            continue
        code = code_match.group(1).strip()

        if title in mapping:
            output_path = mapping[title]
        else:
            output_path = title
            print(f"[!] WARNING: No mapping found for '{title}'. Writing to current directory as '{output_path}'")

        write_file(output_path, code)

    # Parse your new ####### blocks
    hash_blocks = parse_hash_blocks(data)
    if not hash_blocks:
        print("[INFO] No hash-block file entries found in the input file.")

    for filepath, code in hash_blocks:
        filepath = filepath.strip()
        code = code.strip()
        # Here filepath is full or relative path already, so no mapping needed
        write_file(filepath, code)

if __name__ == "__main__":
    main()

