#!/usr/bin/python

import os, re, sys
from pprint import pprint
import datetime
import json
import argparse

ALL = {}
CHILD ={}
DONE = []
QUEUED = []
QUEUED_IMPORTANT = []
HAS_RESULT = []

def recordDirToInt(filepath):
  dirname = os.path.basename(filepath)
  if dirname.isdigit():
    return int(dirname)
  else:
    return 0

def getJson(filePath):
  with open(filePath) as data_file:
    data = json.load(data_file)
    return data

def noteToList(filePath, arr):
  data = getJson(filePath)
  for el in data:
    ALL[el["pathId"]] = el
    if el["parentPathId"] in CHILD:
      CHILD[el["parentPathId"]].add(el["pathId"])
    else:
      CHILD[el["parentPathId"]] = set([el["pathId"]])
    arr.append(el["pathId"])
  return data

def loadRecords():
  for root, dirnames, filenames in \
    sorted(os.walk('record/'), key=lambda (r,d,f):recordDirToInt(r)):
    for fname in sorted(filenames):
      filePath = os.path.join(root, fname)

      if fname=="currentInitialPath":
	data = noteToList(filePath,DONE);
        if os.path.exists(os.path.join(root, "result")):
          HAS_RESULT.append(data[0]["pathId"])
      if fname=="unnecessaryInitialPathsInQueue":
	noteToList(filePath,QUEUED);
      elif fname=="importantInitialPathsInQueue":
	noteToList(filePath,QUEUED_IMPORTANT);

def makeDot(mapList, target):
  toHighlight = set()
  if target:
    curr = target
    while (curr > 1):
      toHighlight.add(curr)
      curr = ALL[curr]["parentPathId"]
    toHighlight.add(curr)

  print "digraph PathGraph {"
  if 1 in toHighlight:
    print "1 [color=blue, style=filled]"
  for k,v in mapList.items():
    for val in v:
      if val in toHighlight:
        print str(val)+" [color=blue, style=filled]"
      elif val == 1:
        # do nothing
        continue
      elif val not in DONE:
        print str(val)+" [color=gray, style=filled]"
#      elif val not in HAS_RESULT:
#        print str(val)+" [color=firebrick, style=filled]"
  for k,v in mapList.items():
    s = str(k) + " -> {"
    for val in v:
      s += str(val) + " "
    s += "}"
    print s
  print "}"
 

def main():
  parser = argparse.ArgumentParser(description='Transform record dir to graphviz dot file.')
  parser.add_argument("-t", "--target", help="Node to be highlighted along with it successors.", type=int)
  args = parser.parse_args()
  loadRecords()
  makeDot(CHILD, args.target)
  print DONE

if __name__ == '__main__':
  main()
