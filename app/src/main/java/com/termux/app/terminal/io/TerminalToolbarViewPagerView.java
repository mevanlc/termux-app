package com.termux.app.terminal.io;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;

public class TerminalToolbarViewPagerView extends ViewPager {

    public TerminalToolbarViewPagerView(@NonNull Context context) {
        super(context);
    }

    public TerminalToolbarViewPagerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean canScroll(View view, boolean checkView, int dx, int x, int y) {
        if (view != null && view.getId() == R.id.terminal_toolbar_text_input)
            return false;

        return super.canScroll(view, checkView, dx, x, y);
    }

}
