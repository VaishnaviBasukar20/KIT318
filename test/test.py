import pandas as pd
import os
import glob
import sys
# You need to  have python and  pandas installed
# Usage python samplepycode.py InputFolderPath OuptutFolderPath
# Assume outputfolder exists
# pandas can be installed using following: pip install pandas


def process_csv_files(inpdir, outdir):
    """
    Reads all CSV files in a directory, and compute average maximum temperature for each station.

    Args:
        inpdir (str): The path to the directory containing the CSV files.
        outdir (str): where output need to be copied (Assume directory exists)
    Returns:
       Success
    """
    try:
        all_files = glob.glob(os.path.join(inpdir, "*.csv"))
        if not all_files:
            print("No CSV files found in the directory")
            return 0

        for f in all_files:
            try:
                # Read CSV with proper column names
                df = pd.read_csv(f, header=None, names=[
                                 'STATION', 'DATE', 'TYPE', 'VALUE', 'FLAG1', 'FLAG2', 'FLAG3', 'FLAG4'])

                # Convert VALUE column to numeric, coercing errors to NaN
                df['VALUE'] = pd.to_numeric(df['VALUE'], errors='coerce')

                # Filter for TMAX records
                df = df[df['TYPE'] == 'TMAX']

                if df.empty:
                    print(f"No TMAX records found in {f}")
                    continue

                # Group by station and calculate mean
                df1 = df.groupby('STATION')['VALUE'].mean().reset_index()

                # Save results
                output_path = os.path.join(outdir, os.path.basename(f))
                df1.to_csv(output_path, index=False, header=False)
                print(f"Processed {f} successfully")

            except Exception as e:
                print(f"Error processing file {f}: {str(e)}")
                continue

        return 1

    except Exception as e:
        print(f"Error in process_csv_files: {str(e)}")
        return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python test.py <input_directory> <output_directory>")
        sys.exit(1)

    success = process_csv_files(sys.argv[1], sys.argv[2])

    if success == 1:
        print("Successful execution")
    else:
        print("Execution failed")
        sys.exit(1)
