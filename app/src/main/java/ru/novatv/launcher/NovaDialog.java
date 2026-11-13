package ru.novatv.launcher;

import android.app.AlertDialog;
import android.content.Context;

final class NovaDialog extends AlertDialog.Builder {
  NovaDialog(Context context) {
    super(context, R.style.NovaDialogTheme);
  }
}
