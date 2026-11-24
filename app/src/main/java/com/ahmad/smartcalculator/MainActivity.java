package com.ahmad.smartcalculator;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView display;
    private String expression = "";
    private boolean justCalculated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 18, 30));
        getWindow().setNavigationBarColor(Color.rgb(15, 18, 30));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(15, 18, 30));

        TextView title = new TextView(this);
        title.setText("SMART CALCULATOR");
        title.setTextColor(Color.rgb(150, 160, 190));
        title.setTextSize(14);
        title.setLetterSpacing(0.12f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(18));
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        display = new TextView(this);
        display.setText("0");
        display.setTextColor(Color.WHITE);
        display.setTextSize(42);
        display.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        display.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        display.setSingleLine(true);
        display.setPadding(dp(8), dp(8), dp(8), dp(18));
        root.addView(display, new LinearLayout.LayoutParams(-1, 0, 1.15f));

        TextView hint = new TextView(this);
        hint.setText("Simple • Fast • Accurate");
        hint.setTextColor(Color.rgb(120, 130, 155));
        hint.setTextSize(12);
        hint.setGravity(Gravity.RIGHT);
        hint.setPadding(0, 0, dp(8), dp(12));
        root.addView(hint);

        String[][] keys = {
            {"AC", "DEL", "%", "÷"},
            {"7", "8", "9", "×"},
            {"4", "5", "6", "−"},
            {"1", "2", "3", "+"},
            {"±", "0", ".", "="}
        };

        for (int r = 0; r < keys.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < keys[r].length; c++) {
                final String key = keys[r][c];
                Button button = new Button(this);
                button.setText(key);
                button.setTextSize(21);
                button.setAllCaps(false);
                button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                button.setTextColor(Color.WHITE);
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonColor(key)));
                button.setElevation(dp(2));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(68), 1);
                p.setMargins(dp(4), dp(5), dp(4), dp(5));
                row.addView(button, p);
                button.setOnClickListener(v -> press(key));
            }
            root.addView(row);
        }

        setContentView(root);
    }

    private int buttonColor(String key) {
        if (key.equals("=")) return Color.rgb(255, 91, 91);
        if (key.equals("÷") || key.equals("×") || key.equals("−") || key.equals("+") || key.equals("%"))
            return Color.rgb(48, 67, 105);
        if (key.equals("AC") || key.equals("DEL") || key.equals("±"))
            return Color.rgb(53, 58, 76);
        return Color.rgb(34, 39, 57);
    }

    private void press(String key) {
        if (key.equals("AC")) {
            expression = "";
            justCalculated = false;
            updateDisplay();
            return;
        }
        if (key.equals("DEL")) {
            if (!expression.isEmpty()) expression = expression.substring(0, expression.length() - 1);
            justCalculated = false;
            updateDisplay();
            return;
        }
        if (key.equals("=")) {
            if (expression.isEmpty()) return;
            try {
                double result = new Parser(expression).parse();
                if (Double.isNaN(result) || Double.isInfinite(result)) throw new ArithmeticException();
                expression = format(result);
                justCalculated = true;
                updateDisplay();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid calculation", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (key.equals("±")) {
            if (!expression.isEmpty()) {
                try {
                    double value = Double.parseDouble(expression);
                    expression = format(-value);
                } catch (Exception ignored) { }
            }
            updateDisplay();
            return;
        }

        String value = key.equals("÷") ? "/" : key.equals("×") ? "*" : key.equals("−") ? "-" : key;
        if (justCalculated && "0123456789.".contains(key)) expression = "";
        justCalculated = false;

        if ("+-*/%".contains(value) && value.length() == 1) {
            if (expression.isEmpty()) {
                if (value.equals("-")) expression = "-";
                else return;
            } else {
                char last = expression.charAt(expression.length() - 1);
                if ("+-*/%".indexOf(last) >= 0) expression = expression.substring(0, expression.length() - 1) + value;
                else expression += value;
            }
        } else {
            expression += value;
        }
        updateDisplay();
    }

    private void updateDisplay() {
        display.setText(expression.isEmpty() ? "0" : expression.replace("/", " ÷ ").replace("*", " × ").replace("-", "−"));
    }

    private String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.8f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static class Parser {
        private final String s;
        private int pos = -1;
        private int ch;
        Parser(String input) { s = input; next(); }
        void next() { ch = (++pos < s.length()) ? s.charAt(pos) : -1; }
        boolean eat(int c) {
            while (ch == ' ') next();
            if (ch == c) { next(); return true; }
            return false;
        }
        double parse() {
            double x = expression();
            while (ch == ' ') next();
            if (pos < s.length()) throw new RuntimeException("Unexpected character");
            return x;
        }
        double expression() {
            double x = term();
            for (;;) {
                if (eat('+')) x += term();
                else if (eat('-')) x -= term();
                else return x;
            }
        }
        double term() {
            double x = factor();
            for (;;) {
                if (eat('*')) x *= factor();
                else if (eat('/')) {
                    double d = factor();
                    if (d == 0) throw new ArithmeticException();
                    x /= d;
                } else if (eat('%')) x %= factor();
                else return x;
            }
        }
        double factor() {
            if (eat('+')) return factor();
            if (eat('-')) return -factor();
            double x;
            int start = pos;
            while ((ch >= '0' && ch <= '9') || ch == '.') next();
            if (pos > start) x = Double.parseDouble(s.substring(start, pos));
            else throw new RuntimeException("Number expected");
            return x;
        }
    }
}
