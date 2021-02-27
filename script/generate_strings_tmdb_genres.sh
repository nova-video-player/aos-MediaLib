#!/bin/sh -x

# API= your tmdb API key
API=$(cat ../res/values/donottranslate.xml | grep tmdb_api_key | sed 's/^.*tmdb_api_key">\([^<]*\)<.*$/\1/g')

# all lang except en
LANG=$(curl -s https://api.themoviedb.org/3/configuration/languages\?api_key=${API} | jq . | grep iso_639_1 | sed 's/^.*"iso_639_1": "\([a-z][a-z]\).*$/\1/g' | grep -v en | sort -u)

for lang in $LANG
do
 curl -s  https://api.themoviedb.org/3/genre/movie/list\?api_key=${API}\&language=${lang} | jq . | grep \"name\": | grep -v null | sed 's/^.*"name": "\([^"]*\)"/\1/g' > result-$lang
done

# remove empty translations
find . -size 0 -print -delete

# en master of all
curl -s  https://api.themoviedb.org/3/genre/movie/list\?api_key=${API}\&language=en | jq . | sed 's/^.*"id": //g' | sed 's/^.*"name": "\([^"]*\)"/\1/g' | sed "/[]{}[]/d" | sed 'N;s/,\n/|/' > result

# assemble result
for f in $(ls result-*)
do
  paste -d '|' result $f > result-tmp
  mv result-tmp result
  paste -d '' strings $f > strings-$f
  sed -i 's/$/<\/string>/g' strings-$f
done

cat result | sed 's/|/ | /g' | sed 's/^ //g' | sed 's/ $//g' > table

LANG=$(ls result-* | sort | sed 's/result-//g')
LANG="en "${LANG}

NB=$(echo $LANG | wc -w)


LINE="| "$(echo $LANG | sed "s/ / | /g")" |"
SEP=$(printf "%${NB}s" | sed 's/ /| --- /g')" |"

echo $LINE > final
echo $SEP >> final
cat table >> final
