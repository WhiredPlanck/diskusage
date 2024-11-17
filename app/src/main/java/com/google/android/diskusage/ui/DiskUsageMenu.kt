package com.google.android.diskusage.ui

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.core.view.forEach
import com.google.android.diskusage.R
import com.google.android.diskusage.databinding.AboutDialogBinding
import com.google.android.diskusage.datasource.SearchManager
import com.google.android.diskusage.filesystem.entity.FileSystemEntry
import com.google.android.diskusage.filesystem.entity.FileSystemSpecial
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot
import com.google.android.diskusage.filesystem.mnt.MountPoint
import com.google.android.diskusage.utils.AppIconCache.getOrLoadBitmap
import com.google.android.diskusage.utils.Logger.Companion.LOGGER
import com.google.android.diskusage.utils.item
import splitties.resources.styledColor

class DiskUsageMenu(val diskusage: DiskUsage) {
    var masterRoot: FileSystemSuperRoot? = null
    private var searchPattern: String? = null
    private val searchManager by lazy { SearchManager(this) }
    private var selectedEntity: FileSystemEntry? = null
    private var searchView: SearchView? = null
    private var origSearchBackground: Drawable? = null
    private lateinit var viewModel: DiskUsageViewModel

    fun onCreate(viewModel: DiskUsageViewModel) {
        this.viewModel = viewModel
//        val actionBar = checkNotNull(diskusage.actionBar)
//        actionBar.setDisplayHomeAsUpEnabled(true)
    }

    fun readyToFinish(): Boolean {
        return true
    }

    fun searchRequest() {
    }

    private fun setupSearchMenuItem(menu: Menu): MenuItem {
        val iconTint = diskusage.styledColor(android.R.attr.colorControlNormal)
        return menu.item(
            R.string.button_search,
            android.R.drawable.ic_search_category_default,
            iconTint,
            true
        ).apply {
            actionView = SearchView(diskusage).also {
                searchView = it
                origSearchBackground = it.background
                if (searchPattern != null) {
                    it.isIconified = false
                    it.setQuery(searchPattern, false)
                }
                it.setOnCloseListener {
                    LOGGER.d("Search process closed")
                    searchPattern = null
                    diskusage.applyPatternNewRoot(masterRoot, null)
                    false
                }
                it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        onQueryTextChange(query)
                        return false
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        LOGGER.d("Search query changed to: %s", newText)
                        searchPattern = newText
                        applyPattern(searchPattern)
                        return true
                    }
                })
            }
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString("search", searchPattern)
    }

    fun onRestoreInstanceState(inState: Bundle) {
        searchPattern = inState.getString("search")
    }

    fun wrapAndSetContentView(view: View?, newRoot: FileSystemSuperRoot?) {
        masterRoot = newRoot
        updateMenu()
        diskusage.setContentView(view)
        diskusage.invalidateOptionsMenu()
    }

    fun applyPattern(searchQuery: String?) {
        if (searchQuery == null || masterRoot == null) return

        if (searchQuery.isEmpty()) {
            searchManager.cancelSearch()
            finishedSearch(masterRoot, searchQuery)
        } else {
            searchManager.search(searchQuery)
        }
    }

    fun finishedSearch(newRoot: FileSystemSuperRoot?, searchQuery: String?): Boolean {
        return if (newRoot != null) {
            searchView?.background = origSearchBackground
            diskusage.applyPatternNewRoot(newRoot, searchQuery)
            true
        } else {
            searchView?.setBackgroundColor(Color.parseColor("#FFDDDD"))
            diskusage.applyPatternNewRoot(masterRoot, searchQuery)
            false
        }
    }

    fun update(position: FileSystemEntry?) {
        this.selectedEntity = position
        updateMenu()
    }

    fun setupToolbarMenu(menu: Menu) {
        setupSearchMenuItem(menu)

        menu.item(R.string.button_show, showAsAction = true) {
            if (selectedEntity != null) {
                diskusage.view(selectedEntity)
            }
        }.apply {
            viewModel.showButton.observe(diskusage) {
                isVisible = it
            }
        }

        menu.item(R.string.button_rescan) {
            diskusage.rescan()
        }.apply {
            viewModel.rescanButton.observe(diskusage) {
                isVisible = it
            }
        }

        menu.item(R.string.button_delete) {
            diskusage.askForDeletion(selectedEntity!!)
        }.apply {
            viewModel.deleteButton.observe(diskusage) {
                isVisible = it
            }
        }

        menu.item(R.string.rederer) {
            diskusage.rendererManager.switchRenderer(masterRoot)
        }.apply {
            viewModel.rendererButtonTitle.observe(diskusage) {
                title = it
            }
        }

        viewModel.toolbarActionButtonVisible.observe(diskusage) {
            menu.forEach { item -> item.isVisible = it }
        }

        menu.forEach { it.isVisible = false }

        menu.item(R.string.action_about) {
            val binding =
                AboutDialogBinding.inflate(
                    LayoutInflater.from(
                        diskusage
                    ), null, false
                )
            binding.sourceCode.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(
                    diskusage.getString(
                        R.string.about_view_source_code,
                        "<b><a href=\"https://github.com/IvanVolosyuk/diskusage\">GitHub</a></b>"
                    ),
                    Html.FROM_HTML_MODE_LEGACY
                )
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(
                    diskusage.getString(
                        R.string.about_view_source_code,
                        "<b><a href=\"https://github.com/IvanVolosyuk/diskusage\">GitHub</a></b>"
                    )
                )
            }
            binding.icon.setImageBitmap(
                getOrLoadBitmap(
                    diskusage,
                    diskusage.applicationInfo,
                    Process.myUid() / 100000,
                    diskusage.resources.getDimensionPixelSize(R.dimen.default_app_icon_size)
                )
            )
            try {
                binding.versionName.text = diskusage.packageManager.getPackageInfo(
                    diskusage.packageName, 0
                ).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                LOGGER.e(e, "Package '${diskusage.packageName}' not found")
            }
            AlertDialog.Builder(diskusage)
                .setView(binding.root)
                .show()
        }
        updateMenu()
    }

    private fun updateMenu() {
        if (diskusage.fileSystemState == null) {
            viewModel.hideToolBarActionButton()
            return
        }

        if (diskusage.fileSystemState.sdcardIsEmpty()) {
            viewModel.hideToolBarActionButton()
            viewModel.enableRescanButton()
        }

        viewModel.showToolbarActionButton()

        val isGPU = diskusage.fileSystemState.isGPU
        val title = if (isGPU) {
            diskusage.getString(R.string.software_renderer)
        } else {
            diskusage.getString(R.string.hardware_renderer)
        }
        viewModel.setRendererButtonTitle(title)

        val view = !(selectedEntity === diskusage.fileSystemState.masterRoot.children[0]
                || selectedEntity is FileSystemSpecial)
        if (view) {
            viewModel.enableRescanButton()
        } else {
            viewModel.disableShowButton()
        }

        val fileOrNotSearching = searchPattern == null || selectedEntity!!.children == null
        val mountPoint = MountPoint.getForKey(diskusage, diskusage.getKey())
        if (view && selectedEntity!!.isDeletable && fileOrNotSearching && mountPoint.isDeleteSupported) {
            viewModel.enableDeleteButton()
        } else {
            viewModel.disableDeleteButton()
        }
    }
}
