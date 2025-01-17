/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.DeleteViewBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.ui.common.FileInfo;
import com.google.android.diskusage.ui.common.FileInfoAdapter;
import timber.log.Timber;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import kotlin.io.FileTreeWalk;
import kotlin.io.FileWalkDirection;

public class DeleteActivity extends Activity {
  public static final String NUM_FILES_KEY = "numFiles";
  public static final String SIZE_KEY = "size";

  Intent responseIntent;

  @Override
  protected void onResume() {
    super.onResume();
//    Debug.startMethodTracing("diskusage");
    FileSystemEntry.setupStrings(this);
    final String path = getIntent().getStringExtra(
        DiskUsage.DELETE_PATH_KEY);
    final String absolutePath = getIntent().getStringExtra(
            DiskUsage.DELETE_ABSOLUTE_PATH_KEY);
    Timber.d("onResume: %s -> %s", path, absolutePath);

    DeleteViewBinding binding = DeleteViewBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    String sizeString = getIntent().getStringExtra(SIZE_KEY);
    int count = getIntent().getIntExtra(NUM_FILES_KEY, 0);
    final File file = new File(absolutePath);
    if (file.exists()) {
      final ArrayList<FileInfo> infos = new ArrayList<>();
      final FileTreeWalk fileTreeWalk = new FileTreeWalk(file, FileWalkDirection.TOP_DOWN);
      for (Iterator<File> it = fileTreeWalk.iterator(); it.hasNext(); ) {
        File sub = it.next();
        if (sub.isFile()) {
          final String size = FileSystemEntry.calcSizeString(sub.length());
          infos.add(new FileInfo(size, sub.getName()));
        } else if (sub.isDirectory()) {
          infos.add(new FileInfo("", sub.getName()));
        }
      }
      binding.list.setLayoutManager(new LinearLayoutManager(this));
      binding.list.setAdapter(new FileInfoAdapter(infos));
    }
    binding.summary.setText(getString(R.string.delete_summary, count, sizeString));

    responseIntent = new Intent();
    responseIntent.putExtra(DiskUsage.DELETE_PATH_KEY, path);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.ask_for_delete_menu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.ask_cancel) {
      setResult(DiskUsage.RESULT_DELETE_CANCELED);
      finish();
      return true;
    } else if (itemId == R.id.ask_delete) {
      setResult(DiskUsage.RESULT_DELETE_CONFIRMED, responseIntent);
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
