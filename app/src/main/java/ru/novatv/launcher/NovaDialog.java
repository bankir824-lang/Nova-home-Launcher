package ru.novatv.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

final class NovaDialog extends AlertDialog.Builder {
  private final int accent;

  NovaDialog(Context context) {
    super(context, R.style.NovaDialogTheme);
    accent = context instanceof MainActivity ? ((MainActivity) context).accent() : NovaUi.DEFAULT_ACCENT;
    getContext().getTheme().applyStyle(accentTheme(accent), true);
  }

  private static int accentTheme(int accent) {
    switch (accent) {
      case 0xffff94b2: return R.style.NovaDialogPink;
      case 0xff8de7d3: return R.style.NovaDialogMint;
      case 0xff9ecaff: return R.style.NovaDialogBlue;
      case 0xfff5f7ff: return R.style.NovaDialogWhite;
      case 0xdd222c40: return R.style.NovaDialogDark;
      default: return R.style.NovaDialogTheme;
    }
  }

  @Override
  public AlertDialog create() {
    AlertDialog dialog = super.create();
    View decor = dialog.getWindow().getDecorView();
    decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View view) {
        view.post(() -> {
          if (dialog.isShowing()) style(dialog);
        });
      }

      @Override public void onViewDetachedFromWindow(View view) {}
    });
    return dialog;
  }

  @Override
  public AlertDialog show() {
    AlertDialog dialog = create();
    dialog.show();
    return dialog;
  }

  private void style(AlertDialog dialog) {
    Context context = dialog.getContext();
    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    View content = dialog.findViewById(android.R.id.content);
    content.setBackground(NovaUi.surface(context));
    ListView list = dialog.getListView();
    if (list != null) {
      list.setSelector(NovaUi.listSelection(context, accent));
      list.setVerticalScrollBarEnabled(false);
    }
    for (int role : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
        AlertDialog.BUTTON_NEUTRAL}) {
      Button button = dialog.getButton(role);
      if (button == null) continue;
      button.setBackground(NovaUi.interaction(context, accent));
      button.setTextColor(NovaUi.TEXT);
      button.setAllCaps(false);
    }
    Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if (list == null && cancel != null && cancel.isShown()
        && "Отмена".contentEquals(cancel.getText())
        && !(dialog.getCurrentFocus() instanceof android.widget.EditText)) cancel.requestFocus();
  }

  static final class IconOptions extends ArrayAdapter<String> {
    private final int[] icons;
    private final int accent;

    IconOptions(MainActivity activity, String[] labels, int[] icons) {
      super(activity, android.R.layout.simple_list_item_1, labels);
      this.icons = icons;
      accent = activity.accent();
    }

    @Override
    public View getView(int position, View reusable, ViewGroup parent) {
      TextView row = (TextView) super.getView(position, reusable, parent);
      row.setTextColor(NovaUi.TEXT);
      row.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[position], 0, 0, 0);
      row.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(accent));
      row.setCompoundDrawablePadding(NovaUi.dp(getContext(), 12));
      return row;
    }
  }
}
