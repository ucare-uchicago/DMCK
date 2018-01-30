#!/usr/bin/env python

import os
import sys


def file_len(filename):
    if os.path.isfile(filename):
        i = -1
        with open(filename) as f:
            for i, l in enumerate(f):
                pass
        return i + 1
    else:
        return 0

def count_success_hit_cache_ratio(param):
    max_records = len(os.listdir(param['dir']))
    if 'num_paths' in param and param['num_paths'] < max_records:
        max_records = param['num_paths']
    
    dat_file = open(param['save_to'], 'w')

    num_hit_cache = 0
    sum_all_events = 0
    for i in xrange(1, max_records + 1):
        ea_rec_path = os.path.join(param['dir'], str(i))
        path_file = os.path.join(ea_rec_path, 'path')
        ev_path = file_len(path_file)
        ev_cons_file = os.path.join(ea_rec_path, 'eventConsequences')
        hit_cache = ev_path - file_len(ev_cons_file)

        num_hit_cache += float(hit_cache)
        sum_all_events += float(ev_path)

        hit_cache_ratio = num_hit_cache / sum_all_events

        content = str(i) + ": " + str(hit_cache_ratio) + "\n"
        dat_file.write(content)

def print_help():
    print "  This script is used to count DMCK Hit Cache Ratio from a historical exploration path."
    print "  Usage: ./.count_dmck_hit_cache_ratio.py --dir /record/dir/path"
    print ""
    print "  --dir, -d        : specify parent of all executed paths logs, the dir with " + \
            "'record' name."
    print "  --num_paths, -np : if specified, evaluation will only be done up to this number " + \
            ", otherwise it tries to cover all paths."
    print "  --save_to, -s    : if specified, result will be saved to specified path, " + \
            ", otherwise will be saved to /tmp/dmck_hit_cache_ratio.dat"

def extract_parameters():
    param = {}
    arg = sys.argv
    param['save_to'] = '/tmp/dmck_hit_cache_ratio.dat'
    for i in xrange(len(arg)):
        if arg[i] == '-d' or arg[i] == '--dir':
            param['dir'] = arg[i+1]
            if not os.path.isdir(param['dir']):
                sys.exit("ERROR: " + param['dir'] + " is not a directory.")
        if arg[i] == '-np' or arg[i] == '--num_paths':
            param['num_paths'] = int(arg[i+1])
            if param['num_paths'] < 1:
                sys.exit("ERROR: Minimum number of paths should be 1.")
        if arg[i] == '-s' or arg[i] == '--save_to':
            param['save_to'] = arg[i+1]
        if arg[i] == '-h' or arg[i] == '--help':
            print_help()
    if not 'dir' in param or param['dir'] is None:
        print_help()
        sys.exit("ERROR: Please follow the minimum instruction.")
    return param

def main():
    param = extract_parameters()
    count_success_hit_cache_ratio(param)
    print "Result is saved to " + param['save_to']

if __name__ == "__main__":
    main()
