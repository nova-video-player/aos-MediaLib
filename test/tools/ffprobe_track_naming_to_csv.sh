#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MEDIALIB_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEFAULT_CSV="$MEDIALIB_DIR/test/resources/track_naming_tests.csv"

append=false
json_out=""

usage() {
    cat <<EOF
Usage: $0 [--append] [--json-out FILE] VIDEO [CSV]

Extract audio/subtitle stream metadata with ffprobe and generate rows for
track_naming_tests.csv.

Without --append, rows are printed to stdout. With --append, rows are appended
to CSV, defaulting to:
  $DEFAULT_CSV
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --append)
            append=true
            shift
            ;;
        --json-out)
            json_out="${2:-}"
            if [[ -z "$json_out" ]]; then
                usage >&2
                exit 2
            fi
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            break
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
        *)
            break
            ;;
    esac
done

if [[ $# -lt 1 || $# -gt 2 ]]; then
    usage >&2
    exit 2
fi

video="$1"
csv="${2:-$DEFAULT_CSV}"

if [[ ! -f "$video" ]]; then
    echo "Video not found: $video" >&2
    exit 1
fi

if ! command -v ffprobe >/dev/null 2>&1; then
    echo "ffprobe is required" >&2
    exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required" >&2
    exit 1
fi

json_file="$json_out"
tmp_json=""
if [[ -z "$json_file" ]]; then
    tmp_json="$(mktemp "${TMPDIR:-/tmp}/track-naming-ffprobe.XXXXXX.json")"
    json_file="$tmp_json"
fi

cleanup() {
    if [[ -n "$tmp_json" ]]; then
        rm -f "$tmp_json"
    fi
}
trap cleanup EXIT

ffprobe -v error -show_streams -of json "$video" > "$json_file"

format_label() {
    local codec="$1"
    local type="$2"
    case "$type:$codec" in
        audio:eac3) echo "EAC3" ;;
        audio:ac3) echo "AC3" ;;
        audio:dts) echo "DTS" ;;
        audio:aac) echo "AAC" ;;
        audio:mp3) echo "MP3" ;;
        audio:opus) echo "OPUS" ;;
        audio:vorbis) echo "VORBIS" ;;
        audio:flac) echo "FLAC" ;;
        subtitle:ass|subtitle:ssa) echo "SSA" ;;
        subtitle:subrip|subtitle:text|subtitle:mov_text) echo "TEXT" ;;
        subtitle:webvtt) echo "VTT" ;;
        subtitle:hdmv_pgs_subtitle) echo "PGS" ;;
        *) printf '%s\n' "$codec" | tr '[:lower:]' '[:upper:]' ;;
    esac
}

lower() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

iso1() {
    case "$(lower "$1")" in
        en|eng) echo "en" ;;
        fr|fre|fra) echo "fr" ;;
        ko|kor) echo "ko" ;;
        und|unknown|"") echo "" ;;
        *) lower "$1" ;;
    esac
}

iso3() {
    case "$(lower "$1")" in
        en|eng) echo "eng" ;;
        fr|fre|fra) echo "fra" ;;
        ko|kor) echo "kor" ;;
        und|unknown|"") echo "" ;;
        *) lower "$1" ;;
    esac
}

language_name() {
    case "$(lower "$1")" in
        en|eng) echo "Anglais" ;;
        fr|fre|fra) echo "Français" ;;
        ko|kor) echo "Coréen" ;;
        und|unknown|"") echo "" ;;
        *) echo "$1" ;;
    esac
}

english_language_name() {
    case "$(lower "$1")" in
        en|eng) echo "English" ;;
        fr|fre|fra) echo "French" ;;
        ko|kor) echo "Korean" ;;
        *) echo "" ;;
    esac
}

capitalize_first() {
    local value="$1"
    if [[ -z "$value" ]]; then
        echo ""
    else
        printf '%s%s\n' "$(printf '%s' "${value:0:1}" | tr '[:lower:]' '[:upper:]')" "${value:1}"
    fi
}

disp_label() {
    local mask="$1"
    local title_first="$2"
    if (( (mask & 0x0080) != 0 )); then echo "Malentendants"; return; fi
    if (( (mask & 0x0004) != 0 )); then echo "original"; return; fi
    if (( (mask & 0x0040) != 0 )); then echo "forced"; return; fi
    if (( (mask & 0x0002) != 0 )); then
        if [[ "$title_first" == "true" ]]; then echo "dubbed"; else echo "traduit"; fi
        return
    fi
    if (( (mask & 0x0100) != 0 )); then
        if [[ "$title_first" == "true" ]]; then echo "audio description"; else echo "visual impaired"; fi
        return
    fi
    if (( (mask & 0x0008) != 0 )); then echo "commentary"; return; fi
    echo ""
}

contains_ci() {
    local haystack
    local needle
    haystack="$(lower "$1")"
    needle="$(lower "$2")"
    [[ -n "$needle" && "$haystack" == *"$needle"* ]]
}

is_redundant_title() {
    local title="$1"
    local lang="$2"
    local lang_name="$3"
    local disp="$4"
    local lower_title
    local lower_disp
    local lower_lang
    lower_title="$(lower "$title")"
    lower_disp="$(lower "$disp")"
    lower_lang="$(lower "$lang_name")"
    local eng_lang
    eng_lang="$(english_language_name "$lang")"
    local lower_eng
    lower_eng="$(lower "$eng_lang")"
    local lang_iso1
    lang_iso1="$(iso1 "$lang")"
    local lang_iso3
    lang_iso3="$(iso3 "$lang")"

    [[ "$lower_title" == "$(lower "$lang_iso1")" ]] && return 0
    [[ "$lower_title" == "$(lower "$lang_iso3")" ]] && return 0
    [[ -n "$lower_lang" && "$lower_title" == "$lower_lang" ]] && return 0
    [[ -n "$lower_eng" && "$lower_title" == "$lower_eng" ]] && return 0
    [[ "$lower_title" == "sdh" ]] && return 0

    if [[ -n "$lower_disp" ]]; then
        [[ -n "$lower_lang" && "$lower_title" == "$lower_lang ($lower_disp)" ]] && return 0
        [[ -n "$lower_lang" && "$lower_title" == "$lower_lang $lower_disp" ]] && return 0
        [[ -n "$lower_eng" && "$lower_title" == "$lower_eng ($lower_disp)" ]] && return 0
        [[ -n "$lower_eng" && "$lower_title" == "$lower_eng $lower_disp" ]] && return 0
        [[ "$lower_disp" == *"malentendants"* && -n "$lower_eng" && "$lower_title" == "$lower_eng (sdh)" ]] && return 0
    fi

    return 1
}

generate_expected() {
    local title="$1"
    local lang="$2"
    local format="$3"
    local mask="$4"
    local title_first="$5"
    local lang_name disp primary lower_primary
    lang_name="$(language_name "$lang")"
    disp="$(disp_label "$mask" "$title_first")"

    if [[ -n "$title" ]] && is_redundant_title "$title" "$lang" "$lang_name" "$disp"; then
        title=""
    fi

    if [[ -n "$title" ]]; then
        primary="$(capitalize_first "$title")"
    elif [[ -n "$lang_name" ]]; then
        primary="$lang_name"
    elif [[ -n "$disp" ]]; then
        primary="$(capitalize_first "$disp")"
    elif [[ -n "$format" ]]; then
        primary="$format"
    else
        primary="Unknown"
    fi

    lower_primary="$(lower "$primary")"
    local secondary=()

    if [[ -n "$lang_name" && "$(lower "$primary")" != "$(lower "$lang_name")" ]]; then
        local eng_lang lang_iso1 lang_iso3 redundant=false
        eng_lang="$(english_language_name "$lang")"
        lang_iso1="$(iso1 "$lang")"
        lang_iso3="$(iso3 "$lang")"
        if contains_ci "$primary" "$lang_name" ||
           contains_ci "$primary" "$eng_lang" ||
           [[ "$lower_primary" == "$(lower "$lang_iso1")" || "$lower_primary" == "$(lower "$lang_iso3")" ]]; then
            redundant=true
        fi
        [[ "$redundant" == "false" ]] && secondary+=("$lang_name")
    fi

    if [[ -n "$disp" && "$(lower "$primary")" != "$(lower "$disp")" ]]; then
        local lower_disp
        lower_disp="$(lower "$disp")"
        local redundant=false
        if contains_ci "$primary" "$disp"; then
            redundant=true
        elif (( (mask & 0x0002) != 0 )) && [[ -n "$lang_name" ]]; then
            redundant=true
        elif [[ "$lower_disp" == *"malentendants"* && "$lower_primary" == *"(sdh)"* ]]; then
            redundant=true
        fi
        [[ "$redundant" == "false" ]] && secondary+=("$(capitalize_first "$disp")")
    fi

    if [[ -n "$format" && "$(lower "$primary")" != "$(lower "$format")" ]]; then
        if ! contains_ci "$primary" "$format"; then
            secondary+=("$format")
        fi
    fi

    if [[ "${#secondary[@]}" -eq 0 ]]; then
        printf '%s\n' "$primary"
        return
    fi

    local sec="${secondary[0]}"
    local i
    for ((i = 1; i < ${#secondary[@]}; i++)); do
        sec+=" (${secondary[$i]})"
    done

    local format_only=false
    if [[ "${#secondary[@]}" -eq 1 && -n "$format" && "${secondary[0]}" == "$format" ]]; then
        format_only=true
    fi

    if [[ "$title_first" == "true" ]]; then
        if [[ "$format_only" == "true" ]]; then
            printf '%s (%s)\n' "$primary" "$sec"
        else
            printf '%s - %s\n' "$primary" "$sec"
        fi
    else
        if [[ "$format_only" == "true" ]]; then
            printf '%s <small>(%s)</small>\n' "$primary" "$sec"
        else
            printf '%s - <small>%s</small>\n' "$primary" "$sec"
        fi
    fi
}

csv_quote() {
    local value="${1//\"/\"\"}"
    if [[ -z "$value" ]]; then
        printf ''
    else
        printf '"%s"' "$value"
    fi
}

rows_file="$(mktemp "${TMPDIR:-/tmp}/track-naming-rows.XXXXXX.csv")"
trap 'cleanup; rm -f "$rows_file"' EXIT

jq -c '
  def bit($name; $value): if (.disposition[$name] // 0) == 1 then $value else 0 end;
  .streams[]
  | select(.codec_type == "audio" or .codec_type == "subtitle")
  | [
      (.tags.title // ""),
      (.tags.language // "und"),
      .codec_name,
      .codec_type,
      (bit("default"; 1)
       + bit("dub"; 2)
       + bit("original"; 4)
       + bit("comment"; 8)
       + bit("lyrics"; 16)
       + bit("karaoke"; 32)
       + bit("forced"; 64)
       + bit("hearing_impaired"; 128)
       + bit("visual_impaired"; 256)
       + bit("clean_effects"; 512)
       + bit("attached_pic"; 1024)
       + bit("captions"; 65536)
       + bit("descriptions"; 131072))
    ]
' "$json_file" | while IFS= read -r row; do
    title="$(printf '%s' "$row" | jq -r '.[0]')"
    lang="$(printf '%s' "$row" | jq -r '.[1]')"
    codec="$(printf '%s' "$row" | jq -r '.[2]')"
    type="$(printf '%s' "$row" | jq -r '.[3]')"
    mask="$(printf '%s' "$row" | jq -r '.[4]')"
    format="$(format_label "$codec" "$type")"
    if [[ "$type" == "audio" ]]; then
        title_first=true
    else
        title_first=false
    fi
    expected="$(generate_expected "$title" "$lang" "$format" "$mask" "$title_first")"
    {
        csv_quote "$title"
        printf ','
        csv_quote "$lang"
        printf ','
        csv_quote "$format"
        printf ',%s,%s,' "$mask" "$title_first"
        csv_quote "$expected"
        printf '\n'
    } >> "$rows_file"
done

if [[ "$append" == "true" ]]; then
    cat "$rows_file" >> "$csv"
    echo "Appended $(wc -l < "$rows_file" | tr -d ' ') rows to $csv" >&2
else
    cat "$rows_file"
fi

if [[ -n "$json_out" ]]; then
    echo "Wrote ffprobe JSON to $json_out" >&2
fi
