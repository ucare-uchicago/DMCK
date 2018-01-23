package edu.uchicago.cs.ucare.dmck.election;

import java.io.Serializable;

import edu.uchicago.cs.ucare.dmck.event.Event;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;

@SuppressWarnings("serial")
public class LeaderElectionVote implements Serializable {

  int sender;
  int role;
  int leader;

  public LeaderElectionVote() {

  }

  public LeaderElectionVote(Event leaderElectionPacket) {
    sender = leaderElectionPacket.getFromId();
    role = (int) leaderElectionPacket.getValue("role");
    leader = (int) leaderElectionPacket.getValue("leader");
  }

  public LeaderElectionVote(int sender, int role, int leader) {
    super();
    this.sender = sender;
    this.role = role;
    this.leader = leader;
  }

  public static boolean isMoreInteresting(LeaderElectionVote newVote, LeaderElectionVote oldVote, int currentRole,
      int currentLeader) {
    if (currentRole == LeaderElectionMain.LOOKING) {
      if (oldVote.role == LeaderElectionMain.LOOKING) {
        if (newVote.role == LeaderElectionMain.LOOKING) {
          if (newVote.leader > oldVote.leader) {
            return true;
          }
        } else {
          return true;
        }
      }
    }
    return false;
  }

  public String toString() {
    return "Vote from " + sender + " : role=" + LeaderElectionMain.getRoleName(role) + ", leader=" + leader;
  }

}