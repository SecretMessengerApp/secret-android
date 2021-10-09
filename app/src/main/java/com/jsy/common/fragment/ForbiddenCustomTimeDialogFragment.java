/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.fragment;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.view.*;
import android.widget.Button;
import com.waz.zclient.R;
import com.ycuwq.datepicker.WheelPicker;

import java.util.ArrayList;
import java.util.List;

public class ForbiddenCustomTimeDialogFragment extends DialogFragment {

    private OnDateChooseListener mOnDateChooseListener;
    private boolean mIsShowAnimation = true;
    protected Button mCancelButton, mDecideButton;

    public void setOnDateChooseListener(OnDateChooseListener onDateChooseListener) {
        mOnDateChooseListener = onDateChooseListener;
    }

    public void showAnimation(boolean show) {
        mIsShowAnimation = show;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_group_forbidden_custom, container);

        List<String> dayList = new ArrayList<>();
        for(int i = 0; i < 30; i++) {
            dayList.add(String.valueOf(i));
        }

        final WheelPicker dayPicker = view.findViewById(R.id.day_picker);
        dayPicker.setDataList(dayList);
        dayPicker.setItemMaximumWidthText("00");
        dayPicker.setShowCurtainBorder(false);
        dayPicker.setShowCurtain(false);
        dayPicker.setCurrentPosition(15);
        dayPicker.setCyclic(true);

        List<String> hourList = new ArrayList<>();
        for(int i = 0; i < 24; i++) {
            hourList.add(String.valueOf(i));
        }

        final WheelPicker hourPicker = view.findViewById(R.id.hour_picker);
        hourPicker.setDataList(hourList);
        hourPicker.setItemMaximumWidthText("00");
        hourPicker.setShowCurtainBorder(false);
        hourPicker.setShowCurtain(false);
        hourPicker.setCurrentPosition(12);
        hourPicker.setCyclic(true);

        List<String> minuteList = new ArrayList<>();
        for(int i = 0; i < 60; i++) {
            minuteList.add(String.valueOf(i));
        }

        final WheelPicker minutePicker = view.findViewById(R.id.minute_picker);
        minutePicker.setItemMaximumWidthText("00");
        minutePicker.setDataList(minuteList);
        minutePicker.setShowCurtainBorder(false);
        minutePicker.setShowCurtain(false);
        minutePicker.setCurrentPosition(30);
        minutePicker.setCyclic(true);

        mCancelButton = view.findViewById(R.id.btn_dialog_date_cancel);
        mDecideButton = view.findViewById(R.id.btn_dialog_date_decide);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        mDecideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mOnDateChooseListener != null) {
                    mOnDateChooseListener.onDateChoose(dayPicker.getCurrentPosition(),
                            hourPicker.getCurrentPosition(), minutePicker.getCurrentPosition());
                }
                dismiss();
            }
        });

        initChild();
        return view;
    }

    protected void initChild() {

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity(), R.style.DatePickerBottomDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        dialog.setContentView(R.layout.dialog_group_forbidden_custom);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if(window != null) {
            if(mIsShowAnimation) {
                window.getAttributes().windowAnimations = R.style.DatePickerDialogAnim;
            }
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.BOTTOM;
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.dimAmount = 0.35f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        return dialog;
    }

    public interface OnDateChooseListener {
        void onDateChoose(int day, int hour, int minute);
    }
}
