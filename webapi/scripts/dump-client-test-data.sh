#!/usr/bin/env bash

# Downloads client test data from Redis into a Zip file.

# A pushd that produces no output.
pushd () {
    command pushd "$@" > /dev/null
}

# A popd that produces no output.
popd () {
    command popd "$@" > /dev/null
}


# The name of the Redis hash in which client test data has been collected.
hash_name=client-test-data

# The name of the Zip file that will contain client test data download from Redis.
zip_file_name=client-test-data.zip

# A file listing the expected contents of that Zip file.
expected_contents_file=$(dirname "$0")/expected-client-test-data.txt

# Make a temporary directory for downloading the data from Redis.
temp_dir=$(mktemp -d)

# The name of the top-level directory inside the temporary directory.
top_dir_name=test-data

# The absolute path of the top-level directory.
top_dir_path=$temp_dir/$top_dir_name
mkdir -p $top_dir_path

# Remove any existing client test data Zip file.
rm -f $zip_file_name

# Iterate over the keys in the Redis hash.
redis-cli hkeys $hash_name | while read relative_file_path
do
  if [ -n "$relative_file_path" ]
  then
    # The absolute path of the file to be downloaded.
    abs_file_path="$top_dir_path/$relative_file_path"

    # Create the directory that will contain the downloaded fiile.
    relative_dirs_to_create=$(dirname "$abs_file_path")
    mkdir -p "$relative_dirs_to_create"

    # Download the file from Redis.
    redis-cli hget $hash_name "$relative_file_path" > "$abs_file_path"
  fi
done

# Create the Zip file.
pushd $temp_dir
zip -rq $zip_file_name .
popd

# Move the Zip file to the directory in which this script was run.
mv $temp_dir/$zip_file_name .

# Delete the temporary directory.
rm -rf $temp_dir

# Remove the hash from Redis.
redis-cli del $hash_name > /dev/null

# Make a temporary file to hold the file paths in the Zip file.
temp_contents=$(mktemp)

# Get the file paths from the Zip file.
zipinfo -1 $zip_file_name | sort > $temp_contents

# Compare them to the expected file paths.
diff -q $expected_contents_file $temp_contents > /dev/null
diff_result=$?

# Return an error if they're different.

if [ $diff_result -ne 0 ]; then
  echo "Generated client test data archive $zip_file_name does not have the expected contents:"
  diff -u $expected_contents_file $temp_contents
else
  echo "Generated client test data archive: $zip_file_name"
fi

rm -f $temp_contents
exit $diff_result
