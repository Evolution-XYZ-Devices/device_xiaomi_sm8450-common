/*
 * Copyright (C) 2018 The LineageOS Project
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

package com.xiaomi.dolby;

import android.media.audiofx.AudioEffect;
import android.util.Log;

import com.xiaomi.dolby.DolbyConstants.DsParam;

import java.util.UUID;

class DolbyAtmos extends AudioEffect {

    private static final String TAG = "XiaomiDolbyAtmos";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final UUID EFFECT_TYPE_DAP =
            UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");

    private static final int
        EFFECT_PARAM_CPDP_VALUES = 5,
        EFFECT_PARAM_ENABLE = 0,
        EFFECT_PARAM_PROFILE = 0xA000000,
        EFFECT_PARAM_SET_PROFILE_PARAMETER = 0x1000000,
        EFFECT_PARAM_GET_PROFILE_PARAMETER = 0x1000005,
        EFFECT_PARAM_RESET_PROFILE_SETTINGS = 0xC000000;

    DolbyAtmos(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, priority, audioSession);
    }

    private static int int32ToByteArray(int value, byte[] dst, int index) {
        dst[index++] = (byte) (value & 0xff);
        dst[index++] = (byte) ((value >>> 8) & 0xff);
        dst[index++] = (byte) ((value >>> 16) & 0xff);
        dst[index] = (byte) ((value >>> 24) & 0xff);
        return 4;
    }

    private static int byteArrayToInt32(byte[] ba) {
        return ((ba[3] & 0xff) << 24) | ((ba[2] & 0xff) << 16)
                | ((ba[1] & 0xff) << 8) | (ba[0] & 0xff);
    }

    private static int int32ArrayToByteArray(int[] src, byte[] dst, int index) {
        for (int x : src) {
            dst[index++] = (byte) ((x >>> 0) & 0xff);
            dst[index++] = (byte) ((x >>> 8) & 0xff);
            dst[index++] = (byte) ((x >>> 16) & 0xff);
            dst[index++] = (byte) ((x >>> 24) & 0xff);
        }
        return src.length << 2;
    }

    private static int[] byteArrayToInt32Array(byte[] ba, int dstLength) {
        int srcLength = ba.length >> 2;
        if (dstLength > srcLength) {
            dstLength = srcLength;
        }
        int[] dst = new int[dstLength];
        for (int i = 0; i < dstLength; i++) {
            dst[i] = ((ba[i * 4 + 3] & 0xff) << 24) | ((ba[i * 4 + 2] & 0xff) << 16)
                    | ((ba[i * 4 + 1] & 0xff) << 8) | (ba[i * 4] & 0xff);
        }
        return dst;
    }

    private void setIntParam(int param, int value) {
        byte[] buf = new byte[12];
        int i = int32ToByteArray(param, buf, 0);
        int32ToByteArray(value, buf, i + int32ToByteArray(1, buf, i));
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf));
    }

    private int getIntParam(int param) {
        byte[] buf = new byte[12];
        int32ToByteArray(param, buf, 0);
        checkStatus(getParameter(EFFECT_PARAM_CPDP_VALUES + param, buf));
        return byteArrayToInt32(buf);
    }

    void setDsOn(boolean on) {
        setIntParam(EFFECT_PARAM_ENABLE, on ? 1 : 0);
        super.setEnabled(on);
    }

    boolean getDsOn() {
        return getIntParam(EFFECT_PARAM_ENABLE) == 1;
    }

    void setProfile(int index) {
        setIntParam(EFFECT_PARAM_PROFILE, index);
    }

    int getProfile() {
        return getIntParam(EFFECT_PARAM_PROFILE);
    }

    void resetProfileSpecificSettings() {
        int profile = getProfile();
        if (DEBUG) Log.d(TAG, "resetProfileSpecificSettings: profile=" + profile);
        setIntParam(EFFECT_PARAM_RESET_PROFILE_SETTINGS, profile);
    }

    void setDapParameter(int profile, DsParam param, int values[]) {
        if (DEBUG) Log.d(TAG, "setDapParameter: profile=" + profile + " param=" + param);
        int length = values.length;
        byte[] buf = new byte[(length + 4) * 4];
        int i = int32ToByteArray(EFFECT_PARAM_SET_PROFILE_PARAMETER, buf, 0);
        int i2 = i + int32ToByteArray(length + 1, buf, i);
        int i3 = i2 + int32ToByteArray(profile, buf, i2);
        int32ArrayToByteArray(values, buf, i3 + int32ToByteArray(param.id, buf, i3));
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf));
    }

    void setDapParameter(DsParam param, int values[]) {
        setDapParameter(getProfile(), param, values);
    }

    void setDapParameterBool(DsParam param, boolean enable) {
        setDapParameter(param, new int[]{enable ? 1 : 0});
    }

    void setDapParameterInt(DsParam param, int value) {
        setDapParameter(param, new int[]{value});
    }

    int[] getDapParameter(int profile, DsParam param) {
        if (DEBUG) Log.d(TAG, "getDapParameter: profile=" + profile + " param=" + param);
        int length = param.length;
        byte[] buf = new byte[(length + 2) * 4];
        int i = (param.id << 16) + EFFECT_PARAM_GET_PROFILE_PARAMETER;
        checkStatus(getParameter(i + (profile << 8), buf));
        return byteArrayToInt32Array(buf, length);
    }

    int[] getDapParameter(DsParam param) {
        return getDapParameter(getProfile(), param);
    }

    boolean getDapParameterBool(DsParam param) {
        return getDapParameter(param)[0] == 1;
    }

    int getDapParameterInt(DsParam param) {
        return getDapParameter(param)[0];
    }
}
