package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
    }

    private int getDefaultRowHeight() {
        TypedValue typedValue = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(android.R.attr.listPreferredItemHeight, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, mActivity.getResources().getDisplayMetrics());
        }
        return Math.round(ViewUtils.dpToPx(mActivity, 48));
    }

    private int getSessionRowHeight() {
        TermuxAppSharedProperties properties = mActivity != null ? mActivity.getProperties() : null;
        if (properties == null) {
            properties = TermuxAppSharedProperties.getProperties();
        }
        return properties != null ? properties.getSessionRowHeight() : TermuxPropertyConstants.DEFAULT_IVALUE_SESSION_ROW_HEIGHT;
    }

    private int getSessionCardFontSize() {
        TermuxAppSharedProperties properties = mActivity != null ? mActivity.getProperties() : null;
        if (properties == null) {
            properties = TermuxAppSharedProperties.getProperties();
        }
        return properties != null ? properties.getSessionCardFontSize() : TermuxPropertyConstants.DEFAULT_IVALUE_SESSION_CARD_FONT_SIZE;
    }

    private boolean isSessionListBottomUp() {
        TermuxAppSharedProperties properties = mActivity != null ? mActivity.getProperties() : null;
        if (properties == null) {
            properties = TermuxAppSharedProperties.getProperties();
        }
        return properties != null && properties.isSessionListBottomUp();
    }

    @Override
    public TermuxSession getItem(int position) {
        if (isSessionListBottomUp()) {
            int count = getCount();
            return super.getItem(count - 1 - position);
        } else {
            return super.getItem(position);
        }
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);

        int rowHeight = getSessionRowHeight();
        int targetHeightPx = rowHeight > 0 ? Math.round(ViewUtils.dpToPx(mActivity, rowHeight)) : getDefaultRowHeight();

        ViewGroup.LayoutParams layoutParams = sessionRowView.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = targetHeightPx;
            sessionRowView.setLayoutParams(layoutParams);
        }
        sessionRowView.setMinimumHeight(targetHeightPx);
        if (sessionTitleView != null) {
            sessionTitleView.setMinHeight(targetHeightPx);
            sessionTitleView.setMinimumHeight(targetHeightPx);
            sessionTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, getSessionCardFontSize());
        }

        TerminalSession sessionAtRow = getItem(position).getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return sessionRowView;
        }

        boolean shouldEnableDarkTheme = ThemeUtils.shouldEnableDarkTheme(mActivity, NightMode.getAppNightMode().getName());

        if (shouldEnableDarkTheme) {
            sessionTitleView.setBackground(
                ContextCompat.getDrawable(mActivity, R.drawable.session_background_black_selected)
            );
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        int indexOfSession = isSessionListBottomUp() ? (getCount() - 1 - position) : position;
        String numberPart = "[" + (indexOfSession + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = shouldEnableDarkTheme ? Color.WHITE : Color.BLACK;
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
        sessionTitleView.setTextColor(color);
        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }

}
