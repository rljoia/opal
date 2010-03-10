package org.obiba.opal.fs;

import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.VFS;

public class OpalFileSystem {

  private final FileObject root;

  public OpalFileSystem(String root) {
    if(root == null) throw new IllegalArgumentException("root cannot be null");

    try {
      FileSystemManager fsm = VFS.getManager();
      FileObject vfsRoot = fsm.resolveFile("file://" + root);
      // This is similar to what chroot does. We obtain a "filesystem" that appears to be rooted at vfsRoot
      this.root = fsm.createVirtualFileSystem(vfsRoot);
    } catch(FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  public FileObject getRoot() {
    return root;
  }

}
