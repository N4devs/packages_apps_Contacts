/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.editor;

import com.android.contacts.model.AccountType.DataKind;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;

/**
 * A dedicated editor for structured name.  When the user collapses/expands
 * the structured name, it will reparse or recompose the name, but only
 * if the user has made changes.  This distinction will be particularly
 * obvious if the name has a non-standard structure. Consider this structure:
 * first name="John Doe", family name="".  As long as the user does not change
 * the full name, expand and collapse will preserve this.  However, if the user
 * changes "John Doe" to "Jane Doe" and then expands the view, we will reparse
 * and show first name="Jane", family name="Doe".
 */
public class StructuredNameEditorView extends TextFieldsEditorView {

    private ContentValues mSnapshot;
    private boolean mChanged;

    public StructuredNameEditorView(Context context) {
        super(context);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        super.setValues(kind, entry, state, readOnly, vig);
        if (mSnapshot == null) {
            mSnapshot = new ContentValues(getValues().getCompleteValues());
            mChanged = entry.isInsert();
        } else {
            mChanged = false;
        }
    }

    @Override
    public void onFieldChanged(String column, String value) {
        if (!isFieldChanged(column, value)) {
            return;
        }

        mChanged = true;

        if (hasShortAndLongForms()) {
            if (areOptionalFieldsVisible()) {
                eraseFullName(getValues());
            } else {
                eraseStructuredName(getValues());
            }
        }

        super.onFieldChanged(column, value);
    }

    @Override
    protected void onOptionalFieldVisibilityChange() {
        if (hasShortAndLongForms()) {
            if (areOptionalFieldsVisible()) {
                switchFromFullNameToStructuredName();
            } else {
                switchFromStructuredNameToFullName();
            }
        }

        super.onOptionalFieldVisibilityChange();
    }

    private void switchFromFullNameToStructuredName() {
        ValuesDelta values = getValues();

        if (!mChanged) {
            values.put(StructuredName.PREFIX,
                    mSnapshot.getAsString(StructuredName.PREFIX));
            values.put(StructuredName.GIVEN_NAME,
                    mSnapshot.getAsString(StructuredName.GIVEN_NAME));
            values.put(StructuredName.MIDDLE_NAME,
                    mSnapshot.getAsString(StructuredName.MIDDLE_NAME));
            values.put(StructuredName.FAMILY_NAME,
                    mSnapshot.getAsString(StructuredName.FAMILY_NAME));
            values.put(StructuredName.SUFFIX,
                    mSnapshot.getAsString(StructuredName.SUFFIX));
            return;
        }

        String displayName = values.getAsString(StructuredName.DISPLAY_NAME);
        Builder builder = ContactsContract.AUTHORITY_URI.buildUpon().appendPath("complete_name");
        appendQueryParameter(builder, StructuredName.DISPLAY_NAME, displayName);
        Cursor cursor = getContext().getContentResolver().query(builder.build(), new String[]{
                StructuredName.PREFIX,
                StructuredName.GIVEN_NAME,
                StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.SUFFIX,
        }, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                eraseFullName(values);
                values.put(StructuredName.PREFIX, cursor.getString(0));
                values.put(StructuredName.GIVEN_NAME, cursor.getString(1));
                values.put(StructuredName.MIDDLE_NAME, cursor.getString(2));
                values.put(StructuredName.FAMILY_NAME, cursor.getString(3));
                values.put(StructuredName.SUFFIX, cursor.getString(4));
            }
        } finally {
            cursor.close();
        }

        mSnapshot.clear();
        mSnapshot.putAll(values.getCompleteValues());
        mSnapshot.put(StructuredName.DISPLAY_NAME, displayName);
    }

    private void switchFromStructuredNameToFullName() {
        ValuesDelta values = getValues();

        if (!mChanged) {
            values.put(StructuredName.DISPLAY_NAME,
                    mSnapshot.getAsString(StructuredName.DISPLAY_NAME));
            return;
        }

        String prefix = values.getAsString(StructuredName.PREFIX);
        String givenName = values.getAsString(StructuredName.GIVEN_NAME);
        String middleName = values.getAsString(StructuredName.MIDDLE_NAME);
        String familyName = values.getAsString(StructuredName.FAMILY_NAME);
        String suffix = values.getAsString(StructuredName.SUFFIX);

        Uri.Builder builder = ContactsContract.AUTHORITY_URI.buildUpon().appendPath(
                "complete_name");
        appendQueryParameter(builder, StructuredName.PREFIX, prefix);
        appendQueryParameter(builder, StructuredName.GIVEN_NAME, givenName);
        appendQueryParameter(builder, StructuredName.MIDDLE_NAME, middleName);
        appendQueryParameter(builder, StructuredName.FAMILY_NAME, familyName);
        appendQueryParameter(builder, StructuredName.SUFFIX, suffix);
        Cursor cursor = getContext().getContentResolver().query(builder.build(), new String[]{
                StructuredName.DISPLAY_NAME,
        }, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                eraseStructuredName(values);
                values.put(StructuredName.DISPLAY_NAME, cursor.getString(0));
            }
        } finally {
            cursor.close();
        }

        mSnapshot.clear();
        mSnapshot.put(StructuredName.DISPLAY_NAME, values.getAsString(StructuredName.DISPLAY_NAME));
        mSnapshot.put(StructuredName.PREFIX, prefix);
        mSnapshot.put(StructuredName.GIVEN_NAME, givenName);
        mSnapshot.put(StructuredName.MIDDLE_NAME, middleName);
        mSnapshot.put(StructuredName.FAMILY_NAME, familyName);
        mSnapshot.put(StructuredName.SUFFIX, suffix);
    }

    private void eraseFullName(ValuesDelta values) {
        values.putNull(StructuredName.DISPLAY_NAME);
    }

    private void eraseStructuredName(ValuesDelta values) {
        values.putNull(StructuredName.PREFIX);
        values.putNull(StructuredName.GIVEN_NAME);
        values.putNull(StructuredName.MIDDLE_NAME);
        values.putNull(StructuredName.FAMILY_NAME);
        values.putNull(StructuredName.SUFFIX);
    }

    private void appendQueryParameter(Uri.Builder builder, String field, String value) {
        if (!TextUtils.isEmpty(value)) {
            builder.appendQueryParameter(field, value);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.mChanged = mChanged;
        state.mSnapshot = mSnapshot;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mChanged = ss.mChanged;
        mSnapshot = ss.mSnapshot;
    }

    private static class SavedState extends BaseSavedState {
        public boolean mChanged;
        public ContentValues mSnapshot;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mChanged = in.readInt() != 0;
            mSnapshot = in.readParcelable(SavedState.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mChanged ? 1 : 0);
            out.writeParcelable(mSnapshot, 0);
        }

        @SuppressWarnings({"unused", "hiding" })
        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
