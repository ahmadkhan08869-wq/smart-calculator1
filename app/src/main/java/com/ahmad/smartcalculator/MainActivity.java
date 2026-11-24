package com.ahmad.smartcalculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.*;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import java.io.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final String PREFS = "amg_private_settings";
    private static final String KEY_ALIAS = "AMG_VAULT_AES_KEY";
    private static final int PICK_MEDIA = 4102;
    private TextView display;
    private String expression = "";
    private boolean justCalculated = false;
    private boolean vaultOpen = false;
    private LinearLayout vaultList;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 12, 28));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 28));
        if (getPreferencesStore().getString("pin_hash", null) == null) showPinSetup();
        showCalculator();
    }

    private android.content.SharedPreferences getPreferencesStore() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void showPinSetup() {
        final EditText first = new EditText(this);
        first.setHint("Create a private PIN (6–12 digits)");
        first.setInputType(2);
        final EditText second = new EditText(this);
        second.setHint("Confirm PIN");
        second.setInputType(2);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(first);
        box.addView(second);
        new AlertDialog.Builder(this).setTitle("Set your private PIN")
            .setMessage("Only you should know this PIN. It will unlock your private gallery.")
            .setView(box).setCancelable(false)
            .setPositiveButton("Save PIN", (d,w) -> {
                String a=first.getText().toString(), b=second.getText().toString();
                if (a.length()<6 || a.length()>12 || !a.matches("[0-9]+") || !a.equals(b)) {
                    Toast.makeText(this,"PIN must be 6–12 digits and both entries must match. Try again.",Toast.LENGTH_LONG).show();
                    showPinSetup();
                } else {
                    getPreferencesStore().edit().putString("pin_hash", hash(a)).apply();
                    Toast.makeText(this,"Private PIN saved on this phone.",Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private String hash(String s) {
        try {
            byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();
            for(byte x:b) out.append(String.format(Locale.US,"%02x",x));
            return out.toString();
        } catch(Exception e) { return ""; }
    }

    private void showCalculator() {
        vaultOpen=false;
        expression="";
        getWindow().setStatusBarColor(Color.rgb(5,12,28));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.setBackgroundColor(Color.rgb(5,10,24));

        TextView title=new TextView(this);
        title.setText("AMG CALCULATOR");
        title.setTextColor(Color.rgb(40,160,255));
        title.setTextSize(17);
        title.setLetterSpacing(.16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));

        display=new TextView(this);
        display.setText("0");
        display.setTextColor(Color.WHITE);
        display.setTextSize(42);
        display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        display.setSingleLine(true);
        display.setPadding(dp(8),dp(8),dp(8),dp(18));
        root.addView(display,new LinearLayout.LayoutParams(-1,0,1));

        String[][] keys={{"AC","DEL","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"±","0",".","="}};
        for(String[] line:keys){
            LinearLayout row=new LinearLayout(this);
            for(String key:line){
                Button b=new Button(this);
                b.setText(key); b.setTextSize(21); b.setAllCaps(false);
                b.setTextColor(Color.WHITE);
                int color=key.equals("=")?Color.rgb(0,130,255):
                    ("÷×−+%".contains(key)&&key.length()==1)?Color.rgb(13,65,125):
                    (key.equals("AC")||key.equals("DEL")||key.equals("±"))?Color.rgb(40,51,75):Color.rgb(22,31,51);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(68),1);
                p.setMargins(dp(4),dp(5),dp(4),dp(5)); row.addView(b,p);
                b.setOnClickListener(v->press(key));
            }
            root.addView(row);
        }
        setContentView(root);
    }

    private void press(String key) {
        if(key.equals("AC")){expression="";justCalculated=false;updateDisplay();return;}
        if(key.equals("DEL")){if(!expression.isEmpty())expression=expression.substring(0,expression.length()-1);justCalculated=false;updateDisplay();return;}
        if(key.equals("=")){
            String saved=getPreferencesStore().getString("pin_hash",null);
            if(saved!=null && hash(expression).equals(saved)){openVault();return;}
            if(expression.isEmpty())return;
            try{double result=new Parser(expression).parse();if(!Double.isFinite(result))throw new Exception();expression=format(result);justCalculated=true;updateDisplay();}
            catch(Exception e){Toast.makeText(this,"Invalid calculation",Toast.LENGTH_SHORT).show();}
            return;
        }
        if(key.equals("±")){try{expression=format(-Double.parseDouble(expression));}catch(Exception ignored){}updateDisplay();return;}
        String v=key.equals("÷")?"/":key.equals("×")?"*":key.equals("−")?"-":key;
        if(justCalculated && "0123456789.".contains(key))expression="";
        justCalculated=false;
        if("+-*/%".contains(v)&&v.length()==1){
            if(expression.isEmpty()){if(v.equals("-"))expression="-";else return;}
            else {char last=expression.charAt(expression.length()-1);if("+-*/%".indexOf(last)>=0)expression=expression.substring(0,expression.length()-1)+v;else expression+=v;}
        } else expression+=v;
        updateDisplay();
    }

    private void updateDisplay(){display.setText(expression.isEmpty()?"0":expression.replace("/"," ÷ ").replace("*"," × ").replace("-","−"));}
    private String format(double n){if(n==Math.rint(n)&&Math.abs(n)<1e15)return String.format(Locale.US,"%.0f",n);return String.format(Locale.US,"%.8f",n).replaceAll("0+$","").replaceAll("\\.$","");}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    private void openVault(){
        vaultOpen=true;
        getWindow().setStatusBarColor(Color.rgb(4,12,28));
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(16),dp(18),dp(12));root.setBackgroundColor(Color.rgb(5,10,24));
        TextView title=new TextView(this);title.setText("AMG PRIVATE GALLERY");title.setTextColor(Color.rgb(40,160,255));title.setTextSize(22);title.setTypeface(Typeface.DEFAULT_BOLD);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView note=new TextView(this);note.setText("Your photos and videos are encrypted on this phone.");note.setTextColor(Color.LTGRAY);note.setTextSize(13);note.setGravity(Gravity.CENTER);root.addView(note);
        Button add=new Button(this);add.setText("+  Add Photos / Videos");add.setTextColor(Color.WHITE);add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0,105,220)));root.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));
        add.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_MEDIA);});
        ScrollView scroll=new ScrollView(this);vaultList=new LinearLayout(this);vaultList.setOrientation(1);scroll.addView(vaultList);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button back=new Button(this);back.setText("Back to Calculator");back.setTextColor(Color.WHITE);back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35,48,72)));root.addView(back);back.setOnClickListener(v->showCalculator());
        setContentView(root);refreshVault();
    }

    private void refreshVault(){
        if(vaultList==null)return;vaultList.removeAllViews();
        File dir=new File(getFilesDir(),"vault");File[] files=dir.listFiles((d,n)->n.endsWith(".amgv"));
        if(files==null||files.length==0){TextView empty=new TextView(this);empty.setText("No photos or videos yet. Tap Add Photos / Videos.");empty.setTextColor(Color.LTGRAY);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(35),0,dp(35));vaultList.addView(empty);return;}
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File file:files){
            String label=file.getName().replace(".amgv","").replace('_',' ');
            TextView item=new TextView(this);item.setText("▣   "+label+"\n      Tap to open  •  Long press to delete");item.setTextColor(Color.WHITE);item.setTextSize(15);item.setPadding(dp(14),dp(16),dp(8),dp(16));item.setBackgroundColor(Color.rgb(17,28,48));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));vaultList.addView(item,p);
            item.setOnClickListener(v->viewEncrypted(file));item.setOnLongClickListener(v->{new AlertDialog.Builder(this).setMessage("Delete this private item?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{file.delete();refreshVault();}).show();return true;});
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_MEDIA && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();try{saveEncrypted(uri);Toast.makeText(this,"Encrypted and saved privately.",Toast.LENGTH_LONG).show();refreshVault();}
            catch(Exception e){Toast.makeText(this,"Could not save this file: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }

    private void saveEncrypted(Uri uri)throws Exception{
        String name="Private_"+System.currentTimeMillis();String mime=getContentResolver().getType(uri);
        Cursor c=getContentResolver().query(uri,null,null,null,null);
        if(c!=null){try{int ix=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&ix>=0)name=c.getString(ix);}finally{c.close();}}
        name=name.replaceAll("[^a-zA-Z0-9._-]","_");if(name.length()>45)name=name.substring(name.length()-45);
        String kind=(mime!=null&&mime.startsWith("video"))?"VIDEO":"PHOTO";
        File dir=new File(getFilesDir(),"vault");if(!dir.exists())dir.mkdirs();
        File out=new File(dir,System.currentTimeMillis()+"_"+kind+"_"+name+".amgv");
        SecretKey key=getVaultKey();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] iv=cipher.getIV();
        try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream fos=new FileOutputStream(out);CipherOutputStream cos=new CipherOutputStream(fos,cipher)){
            fos.write(iv);byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)cos.write(buf,0,n);
        }
    }

    private SecretKey getVaultKey()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(KEY_ALIAS))return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return kg.generateKey();
    }

    private byte[] decrypt(File file)throws Exception{
        try(FileInputStream in=new FileInputStream(file)){
            byte[] iv=new byte[12];if(in.read(iv)!=12)throw new IOException("Bad encrypted file");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,getVaultKey(),new GCMParameterSpec(128,iv));
            ByteArrayOutputStream out=new ByteArrayOutputStream();CipherInputStream cis=new CipherInputStream(in,cipher);byte[] buf=new byte[8192];int n;while((n=cis.read(buf))!=-1)out.write(buf,0,n);cis.close();return out.toByteArray();
        }
    }

    private void viewEncrypted(File file){
        try{
            byte[] data=decrypt(file);
            if(file.getName().contains("_VIDEO_")){
                File temp=File.createTempFile("amg_view_",".mp4",getCacheDir());try(FileOutputStream o=new FileOutputStream(temp)){o.write(data);}
                VideoView video=new VideoView(this);video.setVideoPath(temp.getAbsolutePath());video.setMediaController(new MediaController(this));video.setOnCompletionListener(mp->temp.delete());
                new AlertDialog.Builder(this).setTitle("Private Video").setView(video).setPositiveButton("Close",(d,w)->{video.stopPlayback();temp.delete();}).setOnCancelListener(d->{video.stopPlayback();temp.delete();}).show();video.start();
            }else{
                android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);if(bitmap==null)throw new IOException("Unsupported image");
                ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setMaxHeight(getResources().getDisplayMetrics().heightPixels-dp(140));
                new AlertDialog.Builder(this).setTitle("Private Photo").setView(image).setPositiveButton("Close",null).show();
            }
        }catch(Exception e){Toast.makeText(this,"Unable to open item. "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override public void onBackPressed(){if(vaultOpen)showCalculator();else super.onBackPressed();}

    private static class Parser{
        private final String s;private int pos=-1,ch;Parser(String x){s=x;next();}void next(){ch=(++pos<s.length())?s.charAt(pos):-1;}
        boolean eat(int c){while(ch==' ')next();if(ch==c){next();return true;}return false;}
        double parse(){double x=expr();while(ch==' ')next();if(pos<s.length())throw new RuntimeException();return x;}
        double expr(){double x=term();for(;;){if(eat('+'))x+=term();else if(eat('-'))x-=term();else return x;}}
        double term(){double x=factor();for(;;){if(eat('*'))x*=factor();else if(eat('/')){double d=factor();if(d==0)throw new ArithmeticException();x/=d;}else if(eat('%'))x%=factor();else return x;}}
        double factor(){if(eat('+'))return factor();if(eat('-'))return -factor();int start=pos;while((ch>='0'&&ch<='9')||ch=='.')next();if(pos==start)throw new RuntimeException();return Double.parseDouble(s.substring(start,pos));}
    }
}
