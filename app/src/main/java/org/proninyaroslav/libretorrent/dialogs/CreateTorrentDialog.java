/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.textfield.TextInputEditText;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.databinding.DialogCreateTorrentBinding;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;
import org.proninyaroslav.libretorrent.viewmodel.CreateTorrentViewModel;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class CreateTorrentDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = CreateTorrentDialog.class.getSimpleName();

    private static final String TAG_OPEN_PATH_ERROR_DIALOG = "open_path_error_dialog";
    private static final String TAG_CREATE_FILE_ERROR_DIALOG = "create_file_error_dialog";
    private static final String TAG_FILE_OR_FOLDER_NOT_FOUND_ERROR_DIALOG = "file_or_folder_not_found_error_fialog";
    private static final String TAG_ERROR_FOLDER_IS_EMPTY = "error_folder_is_empty";
    private static final String TAG_ERROR_REPORT_DIALOG = "error_report_dialog";

    private static final int CHOOSE_FILE_REQUEST = 1;
    private static final int CHOOSE_DIR_REQUEST = 2;
    private static final int CHOOSE_PATH_TO_SAVE_REQUEST = 3;

    private AlertDialog alert;
    private AppCompatActivity activity;
    private CreateTorrentViewModel viewModel;
    private DialogCreateTorrentBinding binding;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private ErrorReportDialog errReportDialog;

    public static CreateTorrentDialog newInstance()
    {
        CreateTorrentDialog frag = new CreateTorrentDialog();
        frag.setArguments(new Bundle());

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(activity).get(CreateTorrentViewModel.class);
        dialogViewModel = ViewModelProviders.of(activity).get(BaseAlertDialog.SharedViewModel.class);

        FragmentManager fm = getFragmentManager();
        if (fm != null)
            errReportDialog = (ErrorReportDialog)fm.findFragmentByTag(TAG_ERROR_REPORT_DIALOG);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        /* Back button handle */
        getDialog().setOnKeyListener((DialogInterface dialog, int keyCode, KeyEvent event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return true;
                } else {
                    onBackPressed();
                    return true;
                }
            } else {
                return false;
            }
        });
    }

    @Override
    public void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    switch (event.type) {
                        case POSITIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_ERROR_REPORT_DIALOG) && errReportDialog != null) {
                                Dialog dialog = errReportDialog.getDialog();
                                if (dialog != null) {
                                    TextInputEditText editText = dialog.findViewById(R.id.comment);
                                    Editable e = editText.getText();
                                    String comment = (e == null ? null : e.toString());

                                    Utils.reportError(viewModel.errorReport, comment);
                                    errReportDialog.dismiss();
                                }
                            }
                            break;
                        case NEGATIVE_BUTTON_CLICKED:
                            if (event.dialogTag.equals(TAG_ERROR_REPORT_DIALOG) && errReportDialog != null)
                                errReportDialog.dismiss();
                            break;
                    }
                });
        disposables.add(d);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (activity == null)
            activity = (AppCompatActivity)getActivity();

        LayoutInflater i = LayoutInflater.from(activity);
        binding = DataBindingUtil.inflate(i, R.layout.dialog_create_torrent, null, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);

        initLayoutView();

        return alert;
    }

    private void initLayoutView()
    {
        /* Dismiss error label if user has changed the text */
        binding.trackerUrls.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s)
            {
                binding.layoutTrackerUrls.setErrorEnabled(false);
                binding.layoutTrackerUrls.setError(null);
            }
        });

        binding.webSeedUrls.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s)
            {
                binding.layoutWebSeedUrls.setErrorEnabled(false);
                binding.layoutWebSeedUrls.setError(null);
            }
        });

        binding.piecesSize.setSelection(viewModel.mutableParams.getPieceSizeIndex());
        binding.piecesSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                viewModel.setPiecesSizeIndex(binding.piecesSize.getSelectedItemPosition());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView)
            {
                /* Nothing */
            }
        });

        binding.fileChooserButton.setOnClickListener((v) -> {
            Intent i = new Intent(activity, FileManagerDialog.class);
            FileManagerConfig config = new FileManagerConfig(
                    null,
                    null,
                    FileManagerConfig.FILE_CHOOSER_MODE);
            i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            startActivityForResult(i, CHOOSE_FILE_REQUEST);
        });

        binding.folderChooserButton.setOnClickListener((v) -> {
            Intent i = new Intent(activity, FileManagerDialog.class);
            FileManagerConfig config = new FileManagerConfig(
                    null,
                    null,
                    FileManagerConfig.DIR_CHOOSER_MODE);
            i.putExtra(FileManagerDialog.TAG_CONFIG, config);
            startActivityForResult(i, CHOOSE_DIR_REQUEST);
        });

        initAlertDialog(binding.getRoot());
    }

    private void initAlertDialog(View view)
    {
        alert = new AlertDialog.Builder(activity)
                .setTitle(R.string.create_torrent)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add, null)
                .setView(view)
                .create();


        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener((DialogInterface dialog) -> {
            Button cancelButton = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button addButton = alert.getButton(AlertDialog.BUTTON_POSITIVE);

            viewModel.getState().observe(this, (state) -> {
                if (state.status == CreateTorrentViewModel.BuildState.Status.BUILDING) {
                    alert.setTitle(R.string.creating_torrent_progress);
                    addButton.setVisibility(View.GONE);
                } else {
                    alert.setTitle(R.string.create_torrent);
                    addButton.setVisibility(View.VISIBLE);
                }

                if (state.status == CreateTorrentViewModel.BuildState.Status.FINISHED)
                    handleFinish();

                else if (state.status == CreateTorrentViewModel.BuildState.Status.ERROR)
                    handleBuildError(state.err);
            });

            cancelButton.setOnClickListener((v) ->
                    finish(new Intent(), FragmentCallback.ResultCode.CANCEL));
            addButton.setOnClickListener((v) -> choosePathToSaveDialog());
        });
    }

    private void choosePathToSaveDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(
                null,
                getString(R.string.select_folder_to_save),
                FileManagerConfig.SAVE_FILE_MODE);
        config.mimeType = Utils.MIME_TORRENT;

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        startActivityForResult(i, CHOOSE_PATH_TO_SAVE_REQUEST);
    }

    private void buildTorrent()
    {
        binding.layoutTrackerUrls.setErrorEnabled(false);
        binding.layoutTrackerUrls.setError(null);
        binding.layoutWebSeedUrls.setErrorEnabled(false);
        binding.layoutWebSeedUrls.setError(null);

        viewModel.buildTorrent();
    }

    private void handleBuildError(Throwable err)
    {
        if (err == null)
            return;

        Log.e(TAG, Log.getStackTraceString(err));

        if (err instanceof CreateTorrentViewModel.InvalidTrackerException) {
            binding.layoutTrackerUrls.setErrorEnabled(true);
            binding.layoutTrackerUrls.setError(String.format(getString(R.string.invalid_url),
                    ((CreateTorrentViewModel.InvalidTrackerException)err).url));
            binding.layoutTrackerUrls.requestFocus();

        } else if (err instanceof CreateTorrentViewModel.InvalidWebSeedException) {
            binding.layoutWebSeedUrls.setErrorEnabled(true);
            binding.layoutWebSeedUrls.setError(String.format(getString(R.string.invalid_url),
                    ((CreateTorrentViewModel.InvalidWebSeedException)err).url));
            binding.layoutWebSeedUrls.requestFocus();

        } else if (err instanceof FileNotFoundException) {
            fileOrFolderNotFoundDialog((FileNotFoundException)err);

        } else if (err instanceof IOException) {
            if (err.getMessage().contains("content total size can't be 0"))
                emptyFolderErrorDialog();
            else
                errorReportDialog(err);
        } else {
            errorReportDialog(err);
        }
    }

    private void handleFinish()
    {
        Uri savePath = viewModel.mutableParams.getSavePath();
        if (savePath != null) {
            Toast.makeText(activity.getApplicationContext(),
                    String.format(getString(R.string.torrent_saved_to), savePath.getPath()),
                    Toast.LENGTH_SHORT)
                    .show();
        }
        if (viewModel.mutableParams.isStartSeeding())
            viewModel.downloadTorrent();

        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void openPathErrorDialog(boolean isFile)
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ERROR_FOLDER_IS_EMPTY) == null) {
            BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(isFile ? R.string.unable_to_open_file : R.string.unable_to_open_folder),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            errDialog.show(fm, TAG_ERROR_FOLDER_IS_EMPTY);
        }
    }

    private void fileOrFolderNotFoundDialog(FileNotFoundException e)
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_FILE_OR_FOLDER_NOT_FOUND_ERROR_DIALOG) == null) {
            BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    e.getMessage(),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            errDialog.show(fm, TAG_FILE_OR_FOLDER_NOT_FOUND_ERROR_DIALOG);
        }
    }

    private void emptyFolderErrorDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_OPEN_PATH_ERROR_DIALOG) == null) {
            BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.folder_is_empty),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            errDialog.show(fm, TAG_OPEN_PATH_ERROR_DIALOG);
        }
    }

    private void errorReportDialog(Throwable e)
    {
        viewModel.errorReport = e;

        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_ERROR_REPORT_DIALOG) == null) {
            errReportDialog = ErrorReportDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_create_torrent) + ": " + e.getMessage(),
                    Log.getStackTraceString(e));

            errReportDialog.show(fm, TAG_ERROR_REPORT_DIALOG);
        }
    }

    private void createFileErrorDialog()
    {
        FragmentManager fm = getFragmentManager();
        if (fm != null && fm.findFragmentByTag(TAG_CREATE_FILE_ERROR_DIALOG) == null) {
            BaseAlertDialog createFileErrorDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.unable_to_create_file),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    true);

            createFileErrorDialog.show(fm, TAG_CREATE_FILE_ERROR_DIALOG);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHOOSE_DIR_REQUEST || requestCode == CHOOSE_FILE_REQUEST) {
            if (resultCode != Activity.RESULT_OK)
                return;
            if (data == null || data.getData() == null) {
                openPathErrorDialog(requestCode == CHOOSE_FILE_REQUEST);
                return;
            }

            viewModel.mutableParams.getSeedPath().set(data.getData());

        } else if (requestCode == CHOOSE_PATH_TO_SAVE_REQUEST) {
            if (resultCode != Activity.RESULT_OK)
                return;
            if (data == null || data.getData() == null) {
                createFileErrorDialog();
                return;
            }

            viewModel.mutableParams.setSavePath(data.getData());
            buildTorrent();
        }
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.BACK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        viewModel.finish();
        alert.dismiss();
        ((FragmentCallback)activity).onFragmentFinished(this, intent, code);
    }
}