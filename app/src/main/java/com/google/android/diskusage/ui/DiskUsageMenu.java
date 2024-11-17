package com.google.android.diskusage.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;

import androidx.annotation.NonNull;

import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.AboutDialogBinding;
import com.google.android.diskusage.datasource.SearchManager;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemSpecial;
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.utils.AppIconCache;
import com.google.android.diskusage.utils.Logger;

public class DiskUsageMenu {
    public final DiskUsage diskusage;
    public FileSystemSuperRoot masterRoot;
    protected String searchPattern;
    protected MenuItem searchMenuItem;
    protected MenuItem showMenuItem;
    protected MenuItem rescanMenuItem;
    protected MenuItem deleteMenuItem;
    protected MenuItem rendererMenuItem;
    protected MenuItem aboutMenuItem;
    SearchManager searchManager = new SearchManager(this);
    private FileSystemEntry selectedEntity;
    private SearchView searchView;
    private Drawable origSearchBackground;

    public DiskUsageMenu(DiskUsage diskusage) {
        this.diskusage = diskusage;
    }

    public void onCreate() {
        ActionBar actionBar = diskusage.getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    public boolean readyToFinish() {
        return true;
    }

    public void searchRequest() {}

    public MenuItem makeSearchMenuEntry(Menu menu) {
        MenuItem item = menu.add(R.string.button_search);
        searchView = new SearchView(diskusage);
        origSearchBackground = searchView.getBackground();
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setIcon(android.R.drawable.ic_search_category_default);
        item.setActionView(searchView);
        if (searchPattern != null) {
            searchView.setIconified(false);
            searchView.setQuery(searchPattern, false);
        }
        searchView.setOnCloseListener(() -> {
            Logger.getLOGGER().d("Search process closed");
            searchPattern = null;
            diskusage.applyPatternNewRoot(masterRoot, null);
            return false;
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                onQueryTextChange(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Logger.getLOGGER().d("Search query changed to: %s", newText);
                searchPattern = newText;
                applyPattern(searchPattern);
                return true;
            }
        });
        return item;
    }

    public final void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("search", searchPattern);
    }

    public final void onRestoreInstanceState(@NonNull Bundle inState) {
        searchPattern = inState.getString("search");
    }

    public void wrapAndSetContentView(View view, FileSystemSuperRoot newRoot) {
        this.masterRoot = newRoot;
        updateMenu();
        diskusage.setContentView(view);
        diskusage.invalidateOptionsMenu();
    }

    public void applyPattern(String searchQuery) {
        if (searchQuery == null || masterRoot == null) return;

        if (searchQuery.isEmpty()) {
            searchManager.cancelSearch();
            finishedSearch(masterRoot, searchQuery);
        } else {
            searchManager.search(searchQuery);
        }
    }

    public boolean finishedSearch(FileSystemSuperRoot newRoot, String searchQuery) {
        boolean matched = newRoot != null;
        if (!matched) newRoot = masterRoot;
        diskusage.applyPatternNewRoot(newRoot, searchQuery);
        if (matched) {
            searchView.setBackground(origSearchBackground);
        } else {
            searchView.setBackgroundColor(Color.parseColor("#FFDDDD"));
        }
        return matched;
    }

    public void addRescanMenuEntry(@NonNull Menu menu) {
        menu.add(getString(R.string.button_rescan))
                .setOnMenuItemClickListener(item -> {
                    diskusage.rescan();
                    return true;
                });
    }

    public void update(FileSystemEntry position) {
        this.selectedEntity = position;
        updateMenu();
    }

    private String getString(int id) {
        return diskusage.getString(id);
    }

    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.clear();
        searchMenuItem = makeSearchMenuEntry(menu);

        showMenuItem = menu.add(getString(R.string.button_show));
        showMenuItem.setOnMenuItemClickListener(item -> {
            if (selectedEntity != null) {
                diskusage.view(selectedEntity);
            }
            return true;
        });
        rescanMenuItem = menu.add(getString(R.string.button_rescan));
        rescanMenuItem.setOnMenuItemClickListener(item -> {
            diskusage.rescan();
            return true;
        });

        deleteMenuItem = menu.add(getString(R.string.button_delete));
        deleteMenuItem.setOnMenuItemClickListener(item -> {
            diskusage.askForDeletion(selectedEntity);
            return true;
        });

        rendererMenuItem = menu.add("Renderer");
        rendererMenuItem.setVisible(true);
        rendererMenuItem.setOnMenuItemClickListener(item -> {
            diskusage.rendererManager.switchRenderer(masterRoot);
            return true;
        });

        aboutMenuItem = menu.add(R.string.action_about);
        aboutMenuItem.setOnMenuItemClickListener(item -> {
            final AboutDialogBinding binding =
                    AboutDialogBinding.inflate(LayoutInflater.from(diskusage), null, false);
            binding.sourceCode.setMovementMethod(LinkMovementMethod.getInstance());
            binding.sourceCode.setText(Html.fromHtml(diskusage.getString(
                            R.string.about_view_source_code,
                            "<b><a href=\"https://github.com/IvanVolosyuk/diskusage\">GitHub</a></b>"
                    ))
            );
            binding.icon.setImageBitmap(
                    AppIconCache.getOrLoadBitmap(
                            diskusage,
                            diskusage.getApplicationInfo(),
                            Process.myUid() / 100000,
                            diskusage.getResources().getDimensionPixelSize(R.dimen.default_app_icon_size)
                    )
            );
            try {
                binding.versionName.setText(diskusage.getPackageManager().getPackageInfo(
                        diskusage.getPackageName(), 0
                ).versionName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            new AlertDialog.Builder(diskusage)
                    .setView(binding.getRoot())
                    .show();
            return true;
        });

        updateMenu();
        showMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private void updateMenu() {
        if (showMenuItem == null) return;

        if (diskusage.fileSystemState == null) {
            searchMenuItem.setEnabled(false);
            showMenuItem.setEnabled(false);
            rescanMenuItem.setEnabled(false);
            deleteMenuItem.setEnabled(false);
            rendererMenuItem.setEnabled(false);
            return;
        }

        if (diskusage.fileSystemState.sdcardIsEmpty()) {
            searchMenuItem.setEnabled(false);
            showMenuItem.setEnabled(false);
            rescanMenuItem.setEnabled(true);
            deleteMenuItem.setEnabled(false);
            rendererMenuItem.setEnabled(false);
        }

        rendererMenuItem.setEnabled(true);
        final boolean isGPU = diskusage.fileSystemState.isGPU();
        rendererMenuItem.setTitle(isGPU ? "Software Renderer" : "Hardware Renderer");

        rescanMenuItem.setEnabled(true);
        searchMenuItem.setEnabled(true);


        boolean view = !(selectedEntity == diskusage.fileSystemState.masterRoot.children[0]
                || selectedEntity instanceof FileSystemSpecial);
        showMenuItem.setEnabled(view);

        boolean fileOrNotSearching = searchPattern == null || selectedEntity.children == null;
        MountPoint mountPoint = MountPoint.getForKey(diskusage, diskusage.getKey());
        deleteMenuItem.setEnabled(view && selectedEntity.isDeletable()
                && fileOrNotSearching && mountPoint.isDeleteSupported());
    }
}
