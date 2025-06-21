import re
import os
import sys

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
    try:
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
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
    # Matches blocks like:
    # ##############
    # # path/to/file
    # ##############
    # ```kotlin
    # code...
    # ```
    pattern = (
        r'#{6,}\s*\n'               # line with at least 6 hashes
        r'#\s*(.+?)\s*\n'           # line with filename starting with #
        r'#{6,}\s*\n'               # another line with hashes
        r'```(?:\w+)?\s*\n'         # opening backticks with optional language
        r'(.*?)'                    # code (non-greedy)
        r'```'                      # closing backticks
    )
    return re.findall(pattern, data, re.DOTALL)

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

