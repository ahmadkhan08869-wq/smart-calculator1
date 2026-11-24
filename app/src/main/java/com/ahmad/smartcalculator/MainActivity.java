package com.ahmad.smartcalculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.*;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import androidx.documentfile.provider.DocumentFile;
import android.util.TypedValue;
import java.io.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
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
    private Uri storageFolder;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 12, 28));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 28));
        if (getPreferencesStore().getString("pin_hash", null) == null) showPinSetup();
        showCalculator();
        String savedFolder=getPreferencesStore().getString("storage_folder_uri",null);
        if(savedFolder!=null)storageFolder=Uri.parse(savedFolder);
        if(getPreferencesStore().getString("pin_hash",null)!=null && !getPreferencesStore().getBoolean("folder_prompted",false) && storageFolder==null) display.postDelayed(this::showFolderPrompt,500);
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
                    display.postDelayed(this::showFolderPrompt,500);
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

    private GradientDrawable rounded(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}

    private void showFolderPrompt(){
        new AlertDialog.Builder(this).setTitle("Choose storage folder")
            .setMessage("Select a folder in your phone's file manager. AMG will keep an encrypted copy of your private photos and videos there.")
            .setCancelable(false).setPositiveButton("Choose folder",(d,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i,4103);
            }).setNegativeButton("Use app storage",(d,w)->getPreferencesStore().edit().putBoolean("folder_prompted",true).apply()).show();
    }

    private void copyEncryptedToFolder(File source)throws Exception{
        if(storageFolder==null)return;
        DocumentFile tree=DocumentFile.fromTreeUri(this,storageFolder);
        if(tree==null||!tree.canWrite())return;
        DocumentFile dir=tree.findFile("AMG Private Vault");
        if(dir==null)dir=tree.createDirectory("AMG Private Vault");
        if(dir==null)return;
        DocumentFile target=dir.createFile("application/octet-stream",source.getName());
        if(target==null)return;
        try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(target.getUri())){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
    }

    private void showCalculator() {
        vaultOpen=false; expression="";
        getWindow().setStatusBarColor(Color.rgb(6,12,26));
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(12));root.setBackgroundColor(Color.rgb(6,12,26));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.amg_icon);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);brand.setPadding(dp(12),0,0,0);
        TextView name=new TextView(this);name.setText("AMG CALCULATOR");name.setTextColor(Color.WHITE);name.setTextSize(18);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);brand.addView(name);
        TextView sub=new TextView(this);sub.setText("SMART  •  PRIVATE  •  SECURE");sub.setTextColor(Color.rgb(130,157,195));sub.setTextSize(10);brand.addView(sub);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView lock=new TextView(this);lock.setText("▣");lock.setTextSize(22);lock.setTextColor(Color.rgb(72,170,255));lock.setGravity(Gravity.CENTER);lock.setBackground(rounded(Color.rgb(17,35,62),15,Color.rgb(37,88,145)));header.addView(lock,new LinearLayout.LayoutParams(dp(48),dp(48)));lock.setOnClickListener(v->askPin());
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));
        TextView caption=new TextView(this);caption.setText("STANDARD CALCULATOR");caption.setTextColor(Color.rgb(125,149,182));caption.setTextSize(10);caption.setLetterSpacing(.15f);caption.setPadding(dp(5),dp(20),0,dp(8));root.addView(caption);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(1);screen.setGravity(Gravity.BOTTOM|Gravity.RIGHT);screen.setPadding(dp(18),dp(12),dp(18),dp(16));screen.setBackground(rounded(Color.rgb(17,28,49),22,Color.rgb(34,57,91)));
        TextView small=new TextView(this);small.setText("");small.setTextColor(Color.rgb(136,158,190));small.setTextSize(15);small.setGravity(Gravity.RIGHT);screen.addView(small,new LinearLayout.LayoutParams(-1,dp(26)));
        display=new TextView(this);display.setText("0");display.setTextColor(Color.WHITE);display.setTextSize(42);display.setTypeface(Typeface.DEFAULT,Typeface.BOLD);display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);display.setSingleLine(true);screen.addView(display,new LinearLayout.LayoutParams(-1,dp(70)));
        LinearLayout.LayoutParams screenP=new LinearLayout.LayoutParams(-1,0,1);screenP.setMargins(0,dp(12),0,dp(14));root.addView(screen,screenP);
        String[][] keys={{"AC","DEL","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"±","0",".","="}};
        for(String[] line:keys){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);for(String key:line){Button b=new Button(this);b.setText(key);b.setTextSize(key.equals("=")?24:20);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);
            int color=key.equals("=")?Color.rgb(0,132,255):("÷×−+%".contains(key)&&key.length()==1)?Color.rgb(20,66,117):(key.equals("AC")||key.equals("DEL")||key.equals("±"))?Color.rgb(38,51,75):Color.rgb(25,38,61);
            b.setBackground(rounded(color,17,key.equals("=")?Color.rgb(65,177,255):Color.rgb(36,57,88)));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(65),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(b,p);b.setOnClickListener(v->press(key));}
            root.addView(row,new LinearLayout.LayoutParams(-1,0,1));}
        TextView footer=new TextView(this);footer.setText("Enter your private PIN and tap  =  to unlock gallery");footer.setTextColor(Color.rgb(119,143,177));footer.setTextSize(11);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(9),0,0);root.addView(footer);
        setContentView(root);
    }

    private void askPin(){final EditText pin=new EditText(this);pin.setInputType(2);pin.setSingleLine(true);pin.setHint("Enter private PIN");new AlertDialog.Builder(this).setTitle("Private Gallery").setView(pin).setNegativeButton("Cancel",null).setPositiveButton("Unlock",(d,w)->{String saved=getPreferencesStore().getString("pin_hash",null);if(saved!=null&&hash(pin.getText().toString()).equals(saved))openVault();else Toast.makeText(this,"Incorrect PIN",Toast.LENGTH_SHORT).show();}).show();}


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
        vaultOpen=true;getWindow().setStatusBarColor(Color.rgb(6,12,26));
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(14),dp(16),dp(12));root.setBackgroundColor(Color.rgb(6,12,26));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);title.setText("PRIVATE GALLERY");title.setTextColor(Color.WHITE);title.setTextSize(21);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView secure=new TextView(this);secure.setText("● ENCRYPTED");secure.setTextColor(Color.rgb(63,196,154));secure.setTextSize(10);secure.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.addView(secure);root.addView(header);
        TextView note=new TextView(this);note.setText("Your personal photos and videos");note.setTextColor(Color.rgb(143,162,191));note.setTextSize(13);note.setPadding(0,0,0,dp(14));root.addView(note);
        LinearLayout add=new LinearLayout(this);add.setGravity(Gravity.CENTER);add.setBackground(rounded(Color.rgb(0,108,225),16,Color.rgb(47,159,255)));TextView plus=new TextView(this);plus.setText("＋");plus.setTextColor(Color.WHITE);plus.setTextSize(24);add.addView(plus);TextView addText=new TextView(this);addText.setText("ADD PHOTOS / VIDEOS");addText.setTextColor(Color.WHITE);addText.setTextSize(14);addText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);addText.setPadding(dp(8),0,0,0);add.addView(addText);root.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));
        add.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_MEDIA);});
        TextView storage=new TextView(this);storage.setText("▣  STORAGE FOLDER");storage.setTextColor(Color.rgb(126,163,210));storage.setTextSize(11);storage.setPadding(dp(3),dp(15),0,dp(10));root.addView(storage);storage.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,4103);});
        ScrollView scroll=new ScrollView(this);vaultList=new LinearLayout(this);vaultList.setOrientation(1);scroll.addView(vaultList);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button back=new Button(this);back.setText("BACK TO CALCULATOR");back.setAllCaps(false);back.setTextColor(Color.WHITE);back.setBackground(rounded(Color.rgb(24,39,64),15,Color.rgb(38,62,97)));root.addView(back,new LinearLayout.LayoutParams(-1,dp(50)));back.setOnClickListener(v->showCalculator());
        setContentView(root);refreshVault();
    }


    private void refreshVault(){
        if(vaultList==null)return;vaultList.removeAllViews();
        File dir=new File(getFilesDir(),"vault");File[] files=dir.listFiles((d,n)->n.endsWith(".amgv"));
        if(files==null||files.length==0){LinearLayout empty=new LinearLayout(this);empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(18),dp(48),dp(18),dp(48));TextView icon=new TextView(this);icon.setText("▧");icon.setTextSize(42);icon.setTextColor(Color.rgb(55,125,204));icon.setGravity(Gravity.CENTER);empty.addView(icon);TextView title=new TextView(this);title.setText("Your gallery is empty");title.setTextColor(Color.WHITE);title.setTextSize(17);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setGravity(Gravity.CENTER);empty.addView(title);TextView desc=new TextView(this);desc.setText("Tap Add Photos / Videos to save your first item.");desc.setTextColor(Color.rgb(135,155,185));desc.setTextSize(12);desc.setGravity(Gravity.CENTER);desc.setPadding(0,dp(7),0,0);empty.addView(desc);vaultList.addView(empty);return;}
        Arrays.sort(files,(x,y)->Long.compare(y.lastModified(),x.lastModified()));
        for(File file:files){
            boolean video=file.getName().contains("_VIDEO_");LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(rounded(Color.rgb(17,28,49),16,Color.rgb(34,56,88)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));vaultList.addView(card,cp);
            TextView thumb=new TextView(this);thumb.setText(video?"▶":"▧");thumb.setTextColor(Color.rgb(74,169,255));thumb.setTextSize(26);thumb.setGravity(Gravity.CENTER);thumb.setBackground(rounded(Color.rgb(8,25,48),12,Color.rgb(35,80,128)));card.addView(thumb,new LinearLayout.LayoutParams(dp(58),dp(58)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(12),0,dp(5),0);TextView name=new TextView(this);String label=file.getName().replaceFirst("^[0-9]+_(PHOTO|VIDEO)_","").replace(".amgv","").replace('_',' ');if(label.length()>30)label=label.substring(0,27)+"...";name.setText(label);name.setTextColor(Color.WHITE);name.setTextSize(13);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setMaxLines(2);info.addView(name);
            TextView meta=new TextView(this);meta.setText((video?"VIDEO":"PHOTO")+"  •  ENCRYPTED");meta.setTextColor(Color.rgb(62,190,153));meta.setTextSize(10);meta.setPadding(0,dp(5),0,0);info.addView(meta);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=new TextView(this);more.setText("⋮");more.setTextColor(Color.rgb(145,163,191));more.setTextSize(24);more.setGravity(Gravity.CENTER);card.addView(more,new LinearLayout.LayoutParams(dp(28),dp(48)));
            card.setOnClickListener(v->viewEncrypted(file));more.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{"Open","Delete"},(d,w)->{if(w==0)viewEncrypted(file);else{new AlertDialog.Builder(this).setMessage("Delete this encrypted item permanently?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,ww)->{file.delete();refreshVault();}).show();}}).show());
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==4103 && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(Exception ignored){}
            storageFolder=uri;getPreferencesStore().edit().putString("storage_folder_uri",uri.toString()).putBoolean("folder_prompted",true).apply();
            Toast.makeText(this,"Storage folder selected.",Toast.LENGTH_LONG).show();return;
        }
        if(request==PICK_MEDIA && result==RESULT_OK && data!=null){
            ArrayList<Uri> selected=new ArrayList<>();
            if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}
            else if(data.getData()!=null)selected.add(data.getData());
            int count=0;for(Uri uri:selected){try{saveEncrypted(uri);count++;}catch(Exception e){Toast.makeText(this,"Could not save one selected file.",Toast.LENGTH_SHORT).show();}}
            Toast.makeText(this,count+" item(s) encrypted and saved.",Toast.LENGTH_LONG).show();refreshVault();
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
        copyEncryptedToFolder(out);
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
