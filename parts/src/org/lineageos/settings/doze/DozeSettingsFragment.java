/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.doze;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

import com.android.settingslib.widget.MainSwitchPreference;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class DozeSettingsFragment extends PreferenceFragment
        implements OnPreferenceChangeListener, OnCheckedChangeListener {
    private MainSwitchPreference mSwitchBar;

    private SwitchPreference mAlwaysOnDisplayPreference;
    private ListPreference mDozeBrightnessPreference;

    private Handler mHandler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.doze_settings);

        SharedPreferences prefs =
                getActivity().getSharedPreferences("doze_settings", Activity.MODE_PRIVATE);
        if (savedInstanceState == null && !prefs.getBoolean("first_help_shown", false)) {
            showHelp();
        }

        boolean dozeEnabled = DozeUtils.isDozeEnabled(getActivity());

        mSwitchBar = (MainSwitchPreference) findPreference(DozeUtils.DOZE_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(dozeEnabled);

        mAlwaysOnDisplayPreference = (SwitchPreference) findPreference(DozeUtils.ALWAYS_ON_DISPLAY);
        mAlwaysOnDisplayPreference.setEnabled(dozeEnabled);
        mAlwaysOnDisplayPreference.setChecked(DozeUtils.isAlwaysOnEnabled(getActivity()));
        mAlwaysOnDisplayPreference.setOnPreferenceChangeListener(this);

        mDozeBrightnessPreference = (ListPreference) findPreference(DozeUtils.DOZE_BRIGHTNESS_KEY);
        mDozeBrightnessPreference.setEnabled(
                dozeEnabled && DozeUtils.isAlwaysOnEnabled(getActivity()));
        mDozeBrightnessPreference.setOnPreferenceChangeListener(this);

        // Hide AOD and doze brightness if not supported and set all its dependents otherwise
        if (!DozeUtils.alwaysOnDisplayAvailable(getActivity())) {
            getPreferenceScreen().removePreference(mAlwaysOnDisplayPreference);
            getPreferenceScreen().removePreference(mDozeBrightnessPreference);
        } else {
            if (!FileUtils.isFileWritable(DozeUtils.DOZE_MODE_PATH)) {
                getPreferenceScreen().removePreference(mDozeBrightnessPreference);
            } else {
                DozeUtils.updateDozeBrightnessIcon(getContext(), mDozeBrightnessPreference);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DozeUtils.ALWAYS_ON_DISPLAY.equals(preference.getKey())) {
            DozeUtils.enableAlwaysOn(getActivity(), (Boolean) newValue);
            if (!(Boolean) newValue) {
                mDozeBrightnessPreference.setValue(DozeUtils.DOZE_BRIGHTNESS_LBM);
                DozeUtils.setDozeMode(DozeUtils.DOZE_BRIGHTNESS_LBM);
            }
            mDozeBrightnessPreference.setEnabled((Boolean) newValue);
        } else if (DozeUtils.DOZE_BRIGHTNESS_KEY.equals(preference.getKey())) {
            if (!DozeUtils.DOZE_BRIGHTNESS_AUTO.equals((String) newValue)) {
                DozeUtils.setDozeMode((String) newValue);
            }
        }

        mHandler.post(() -> {
            DozeUtils.checkDozeService(getActivity());
            DozeUtils.updateDozeBrightnessIcon(getContext(), mDozeBrightnessPreference);
        });

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        DozeUtils.enableDoze(getActivity(), isChecked);
        DozeUtils.checkDozeService(getActivity());

        mSwitchBar.setChecked(isChecked);

        if (!isChecked) {
            DozeUtils.enableAlwaysOn(getActivity(), false);
            mAlwaysOnDisplayPreference.setChecked(false);
            mDozeBrightnessPreference.setValue(DozeUtils.DOZE_BRIGHTNESS_LBM);
            DozeUtils.updateDozeBrightnessIcon(getContext(), mDozeBrightnessPreference);
        }
        mAlwaysOnDisplayPreference.setEnabled(isChecked);
        mDozeBrightnessPreference.setEnabled(
                isChecked && DozeUtils.isAlwaysOnEnabled(getActivity()));
    }

    public static class HelpDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.doze_settings_help_title)
                    .setMessage(R.string.doze_settings_help_text)
                    .setNegativeButton(R.string.dialog_ok, (dialog, which) -> dialog.cancel())
                    .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            getActivity()
                    .getSharedPreferences("doze_settings", Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean("first_help_shown", true)
                    .commit();
        }
    }

    private void showHelp() {
        HelpDialogFragment fragment = new HelpDialogFragment();
        fragment.show(getFragmentManager(), "help_dialog");
    }
}
