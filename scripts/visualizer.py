#!/usr/bin/python
import os, re, sys
from pprint import pprint
import datetime
import json
import argparse
import ast

tid = re.compile('.+ tid=([\-0-9]+) ')
recv = re.compile('.+recvNode=([0-9]+)')
send = re.compile('.+sendNode=([0-9]+)')
msg = re.compile('.+payload=(.+), recvNode=')

def readFile(filepath):
  initPath = {}
  f = open(filepath, 'r');
  idx = 0
  for line in f:
    idx += 1
    i = line.index(" [[")
    header = line[:i]
    clocks = line[i+1:]
    vclock = ast.literal_eval(clocks)
    myTid = tid.match(header).group(1)
    sender =  send.match(header).group(1)
    receiver = recv.match(header).group(1)
    payload = msg.match(header).group(1)
    entry = {"idx":idx, "tid":myTid, "from":int(sender), "to":int(receiver), "vclock":vclock, "msg":payload}
    initPath[idx] = entry

  return initPath


def happenBefore(vc1, vc2):
  flat1 = [x for sublist in vc1 for x in sublist]
  flat2 = [x for sublist in vc2 for x in sublist]
  for i in xrange(0,len(flat1)):
    if flat1[i] > flat2[i]:
      return False
          
  return True


def visualize(ev):
  grouped = set()
  groups = []
  while len(ev) > len(grouped):
    mygroup = []
    for k,v in ev.iteritems():
      if k in grouped:
        continue

      if mygroup == []:
        mygroup.append(k)
        grouped.add(k)
      else:
        concurrent = True;
        for idx in mygroup:
          if happenBefore(ev[idx]["vclock"],v["vclock"]):
            concurrent = False
        if concurrent:
          mygroup.append(k)
          grouped.add(k)
    groups.append(mygroup)

  maxNode = -1
  for k,v in ev.iteritems():
    if v["from"] > maxNode:
      maxNode = v["from"]
    if v["to"] > maxNode:
      maxNode = v["to"]

  padSize = 7
  strF = "{:>"+str(padSize-2)+"} |"
  rowSep = ("{:-<"+str((maxNode+2)*padSize)+"}").format("")

  header = strF.format("eId")
  for i in xrange(0,maxNode+1):
    header += strF.format(str(i))
  print header
  print rowSep

  for group in groups:
    for idx in group:
      send = ev[idx]["from"]
      recv = ev[idx]["to"]
      line = strF.format(idx)
      dir = 1 if send < recv else 0
      
      for i in xrange(0,maxNode+1):
        if i==send and i==recv:
          line += strF.format("(" + str(send) + ")")
        elif i==send:
          cell = str(send) + ">" if dir else "<" + str(send)
          line += strF.format(cell)
        elif i==recv:
          cell = ">" + str(recv) if dir else str(recv) + "<"
          line += strF.format(cell)
        else:
          line += strF.format("")
      line += " " + ev[idx]["msg"]
      print line
    print rowSep

    


def main():
  parser = argparse.ArgumentParser(description='Visualize initial path')
  parser.add_argument("filepath", help="path to initial path file")
  args = parser.parse_args()
  initPath = readFile(args.filepath)
  visualize(initPath)

if __name__ == '__main__':
  main()

