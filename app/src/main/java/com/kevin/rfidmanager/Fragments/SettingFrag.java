package com.kevin.rfidmanager.Fragments;

/**
 * Created by Kevin on 2017/1/26.
 */

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.kevin.rfidmanager.Activity.LoginActivity;
import com.kevin.rfidmanager.Activity.MainActivity;
import com.kevin.rfidmanager.MyApplication;
import com.kevin.rfidmanager.R;
import com.kevin.rfidmanager.Utils.DatabaseUtil;
import com.kevin.rfidmanager.Utils.SPUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import at.markushi.ui.CircleButton;

public class SettingFrag extends android.support.v4.app.Fragment {
    Button backupDatabaseButton, restoreDatabaseButton, changePasswordButton, changeRFIDRangeButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.setting_layout, container, false);
        initUI(v);
        return v;
    }

    private void initUI(View v) {
        backupDatabaseButton = (Button) v.findViewById(R.id.backup_database_button);
        backupDatabaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backupDialog();
            }
        });
        restoreDatabaseButton = (Button) v.findViewById(R.id.restore_database_button);
        restoreDatabaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreDialog();
            }
        });
        changePasswordButton = (Button) v.findViewById(R.id.change_password);
        changePasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPasswordChangeDialog();
            }
        });

        changeRFIDRangeButton = (Button) v.findViewById(R.id.change_rfid_range);

    }

    /*
    This is a dialog used for changing password.
     */
    public void showPasswordChangeDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.password_change_dialog_layout, null);
        dialogBuilder.setView(dialogView);

        final EditText oldPasswordEdt = (EditText) dialogView.findViewById(R.id.old_password_editor);
        final EditText newPasswordEdt = (EditText) dialogView.findViewById(R.id.new_password_editor);
        final EditText confirmNewPasswordEdt = (EditText) dialogView.findViewById(R.id.confirm_new_password);
        final TextView message = (TextView) dialogView.findViewById(R.id.message_text_login);
        final Button saveButton = (Button) dialogView.findViewById(R.id.dialog_change);
        final Button cancleButton = (Button) dialogView.findViewById(R.id.dialog_cancle);

        dialogBuilder.setTitle(getResources().getString(R.string.change_passwd));
        final AlertDialog b = dialogBuilder.create();
        b.show();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // check current password
                if (!SPUtil.getInstence(getActivity().getApplicationContext()).getPassWord().
                        equals(oldPasswordEdt.getText().toString())) {
                    message.setText(R.string.wrong_old_password);
                    message.setTextColor(getResources().getColor(R.color.warning_color));
                    return;
                }
                // check password of two text editors
                if (!newPasswordEdt.getText().toString().
                        equals(confirmNewPasswordEdt.getText().toString())) {
                    message.setText(R.string.diff_passwd);
                    message.setTextColor(getResources().getColor(R.color.warning_color));
                    return;
                }
                //save password with edt.getText().toString();
                SPUtil us = SPUtil.getInstence(getActivity().getApplicationContext());
                us.savePassWord(newPasswordEdt.getText().toString());
                Toast.makeText(getActivity().getApplicationContext(), R.string.password_updated, Toast.LENGTH_LONG).
                        show();
                b.dismiss();
            }
        });

        cancleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //dismiss dialog
                b.dismiss();
            }
        });
    }

    /*
    This is a dialog used for backup database
     */
    public void backupDialog() {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_backup_layout, null);
        dialogBuilder.setView(dialogView);

        final TextView textView = (TextView) dialogView.findViewById(R.id.backup_dialog_message);
        final CircleButton internal_backup = (CircleButton) dialogView.findViewById(R.id.store_internal_space);
        final CircleButton sd_backup = (CircleButton) dialogView.findViewById(R.id.store_sd_space);

        internal_backup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity().getFilesDir().canWrite()){
                    try {
                        File backupDB = getActivity().getDatabasePath(getActivity().getString(R.string.database_name));
                        String backupDBPath = String.format("%s.bak", getActivity().getString(R.string.database_name));
                        File currentDB = new File(getActivity().getFilesDir(), backupDBPath);
                        currentDB.createNewFile();
                        FileChannel src = new FileInputStream(currentDB).getChannel();
                        FileChannel dst = new FileOutputStream(backupDB).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();
                        textView.setText(R.string.backup_internal_successful);
                    } catch (Exception e) {
                        e.printStackTrace();
                        textView.setText(R.string.internal_memory_read_fail);
                    }
                }else
                    textView.setText(R.string.internal_memory_read_fail);

            }
        });

        sd_backup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File sd = Environment.getExternalStorageDirectory();
                if (sd.canWrite()) {
                    try {

                        File backupDB = getActivity().getDatabasePath(getActivity().getString(R.string.database_name));
                        String backupDBPath = String.format("%s.bak", getActivity().getString(R.string.database_name));
                        File currentDB = new File(sd, backupDBPath);

                        FileChannel src = new FileInputStream(currentDB).getChannel();
                        FileChannel dst = new FileOutputStream(backupDB).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();
                        textView.setText(R.string.backup_successful);
                    } catch (Exception e) {
                        e.printStackTrace();
                        textView.setText(R.string.no_tf);
                    }

                } else
                    textView.setText(R.string.no_tf);
            }
        });

        dialogBuilder.setTitle(R.string.select_backup_position);
        dialogBuilder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        final AlertDialog b = dialogBuilder.create();
        b.show();

    }

    /*
    This is a dialog used for backup database
     */
    public void restoreDialog() {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_backup_layout, null);
        dialogBuilder.setView(dialogView);

        final TextView textView = (TextView) dialogView.findViewById(R.id.backup_dialog_message);
        final CircleButton internal_backup = (CircleButton) dialogView.findViewById(R.id.store_internal_space);
        final CircleButton sd_backup = (CircleButton) dialogView.findViewById(R.id.store_sd_space);

        internal_backup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity().getFilesDir().canWrite()){
                    try {
                        String backupDBPath = String.format("%s.bak", getActivity().getString(R.string.database_name));
                        File currentDB = getActivity().getDatabasePath(getActivity().getString(R.string.database_name));
                        File backupDB = new File(getActivity().getFilesDir(), backupDBPath);
                        if (!backupDB.exists()){
                            textView.setText(R.string.no_backup_File_data);
                        }
                        FileChannel src = new FileInputStream(currentDB).getChannel();
                        FileChannel dst = new FileOutputStream(backupDB).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();
                        textView.setText(R.string.restore_successful);
                    } catch (Exception e) {
                        e.printStackTrace();
                        textView.setText(R.string.internal_memory_read_fail);
                    }
                }else
                    textView.setText(R.string.internal_memory_read_fail);

            }
        });

        sd_backup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File sd = Environment.getExternalStorageDirectory();
                if (sd.canWrite()) {
                    try {

                        String backupDBPath = String.format("%s.bak", getActivity().getString(R.string.database_name));
                        File currentDB = getActivity().getDatabasePath(getActivity().getString(R.string.database_name));
                        File backupDB = new File(sd, backupDBPath);

                        if (!backupDB.exists()){
                            textView.setText(R.string.no_backup_File_TF);
                        }

                        FileChannel src = new FileInputStream(currentDB).getChannel();
                        FileChannel dst = new FileOutputStream(backupDB).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();
                        textView.setText(R.string.restore_successful);
                    } catch (Exception e) {
                        e.printStackTrace();
                        textView.setText(R.string.no_tf);
                    }

                } else
                    textView.setText(R.string.no_tf);
            }
        });

        dialogBuilder.setTitle(R.string.select_restore_position);
        dialogBuilder.setMessage(R.string.restore_warning);
        dialogBuilder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        final AlertDialog b = dialogBuilder.create();
        b.show();

    }


}
