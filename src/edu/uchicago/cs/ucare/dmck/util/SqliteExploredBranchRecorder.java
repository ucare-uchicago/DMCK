package edu.uchicago.cs.ucare.dmck.util;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

public class SqliteExploredBranchRecorder implements ExploredBranchRecorder {

  final static Logger LOG = LoggerFactory.getLogger(FileSystemExploredBranchRecorder.class);

  String dbDir;
  int currentDepth;
  public String path;
  SQLiteConnection db;
  SQLiteStatement createChild;
  SQLiteStatement finishChild;
  // SQLiteStatement finishChildWithNote;
  SQLiteStatement isChildFinished;
  SQLiteStatement createNote;
  SQLiteStatement replaceNote;
  SQLiteStatement updateNote;
  SQLiteStatement readNote;

  static {
    java.util.logging.Logger.getLogger("com.almworks.sqlite4java").setLevel(java.util.logging.Level.WARNING);
  }

  public SqliteExploredBranchRecorder(String dbDir) throws SQLiteException {
    this.dbDir = dbDir;
    db = new SQLiteConnection(new File(dbDir + "/db"));
    db.open(true);
    db.exec("CREATE TABLE IF NOT EXISTS EXPLORE_PATH (path TEXT PRIMARY KEY, " + "is_finished INTEGER)");
    db.exec("CREATE INDEX IF NOT EXISTS EXPLORE_PATH_INDEX ON EXPLORE_PATH " + "(path)");
    db.exec("CREATE TABLE IF NOT EXISTS NOTE (path TEXT, note_key TEXT, "
        + "note_value TEXT, PRIMARY KEY (path, note_key))");
    db.exec("CREATE INDEX IF NOT EXISTS NOTE_INDEX ON NOTE (path, note_key)");

    createChild = db.prepare("INSERT OR IGNORE INTO EXPLORE_PATH (path, is_finished) " + "VALUES (?, 0)");
    finishChild = db.prepare("UPDATE EXPLORE_PATH SET is_finished=1 WHERE path=?");
    // finishChildWithNote = db.prepare("UPDATE EXPLORE_PATH SET
    // is_finished=1, finished_note=? WHERE path=?");
    isChildFinished = db.prepare("SELECT is_finished FROM EXPLORE_PATH WHERE path=?");
    createNote = db.prepare("INSERT OR IGNORE INTO NOTE (path, note_key, note_value) " + "VALUES (?, ?, ?)");
    replaceNote = db.prepare("INSERT OR REPLACE INTO NOTE (path, note_key, note_value) " + "VALUES (?, ?, ?)");
    updateNote = db.prepare("UPDATE NOTE SET note_value=? WHERE path=? AND note_key=?");
    readNote = db.prepare("SELECT note_value FROM NOTE WHERE path=? AND note_key=?");
    resetTraversal();
  }

  public void markBelowSubtreeFinished() {
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Marked " + path + " to be explored");
      }
      LOG.info("Marked " + path + " to be explored");
      finishChild.reset();
      finishChild.bind(1, path);
      finishChild.step();
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
  }

  public void markBelowSubtreeFinished(String note) {
    try {
      finishChild.reset();
      replaceNote.reset();
      finishChild.bind(1, 1);
      finishChild.step();
      replaceNote.bind(1, path);
      replaceNote.bind(2, "finished");
      replaceNote.bind(3, note);
      replaceNote.step();
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
  }

  public boolean isSubtreeBelowFinished() {
    try {
      isChildFinished.reset();
      isChildFinished.bind(1, path);
      boolean result = isChildFinished.step();
      if (result) {
        return isChildFinished.columnInt(0) == 1;
      } else {
        return false;
      }
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
    return false;
  }

  public boolean isSubtreeBelowChildFinished(long child) {
    try {
      isChildFinished.reset();
      isChildFinished.bind(1, path + "/" + child);
      boolean result = isChildFinished.step();
      if (result) {
        return isChildFinished.columnInt(0) == 1;
      } else {
        return false;
      }
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
    return false;
  }

  public boolean isSubtreeBelowChildrenFinished(long[] children) {
    String tmpPath = path;
    for (long child : children) {
      tmpPath += ("/" + child);
    }
    LOG.info("korn is finished " + tmpPath);
    try {
      isChildFinished.reset();
      isChildFinished.bind(1, tmpPath);
      boolean result = isChildFinished.step();
      if (result) {
        return isChildFinished.columnInt(0) == 1;
      } else {
        return false;
      }
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
    return false;
  }

  public boolean createChild(long child) {
    try {
      createChild.reset();
      createChild.bind(1, path + "/" + child);
      createChild.step();
      return true;
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
    return false;
  }

  public boolean noteThisNode(String key, String value) {
    return noteThisNode(key, value, true);
  }

  public boolean noteThisNode(String key, String value, boolean overwrite) {
    if (overwrite) {
      try {
        replaceNote.reset();
        replaceNote.bind(1, path);
        replaceNote.bind(2, key);
        replaceNote.bind(3, value);
        replaceNote.step();
        return true;
      } catch (SQLiteException e) {
        LOG.error(e.getLocalizedMessage(), e);
      }
    } else {
      try {
        createNote.reset();
        createNote.bind(1, path);
        createNote.bind(2, key);
        createNote.bind(3, value);
        createNote.step();
        return true;
      } catch (SQLiteException e) {
        LOG.error(e.getLocalizedMessage(), e);
      }
    }
    return false;
  }

  public boolean noteThisNode(String key, byte[] value) {
    return noteThisNode(key, value, true);
  }

  public boolean noteThisNode(String key, byte[] value, boolean overwrite) {
    return noteThisNode(key, new String(value), overwrite);
  }

  public byte[] readThisNode(String key) {
    try {
      readNote.reset();
      readNote.bind(1, path);
      readNote.bind(2, key);
      if (readNote.step()) {
        return readNote.columnString(0).getBytes();
      } else {
        return null;
      }
    } catch (SQLiteException e) {
      LOG.error(e.getLocalizedMessage(), e);
    }
    return null;
  }

  public void traverseUpward(int hop) {
    for (int i = 0; i < hop && currentDepth > 0; ++i, --currentDepth) {
      path = path.substring(0, path.lastIndexOf('/'));
    }
  }

  public void traverseDownTo(long child) {
    path = path + "/" + child;
    ++currentDepth;
  }

  public int getCurrentDepth() {
    return currentDepth;
  }

  public void resetTraversal() {
    currentDepth = 0;
    path = "/root";
  }

  public String getCurrentPath() {
    return path;
  }

}
