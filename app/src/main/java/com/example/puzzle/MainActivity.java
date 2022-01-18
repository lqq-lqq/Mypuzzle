package com.example.puzzle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.IntentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomappbar.BottomAppBarTopEdgeTreatment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.CheckedOutputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private int column=3;
    private int line=3;
    private ImageView[][] block=new ImageView[line][column+1];    //装小块拼图的ImageView的数组
    private Bitmap[][] originalPic=new Bitmap[line][column+1];    //小块拼图原本的图片碎片
    private Bitmap[][] currentPic=new Bitmap[line][column+1];    //小块拼图现在的图片碎片
    private int blankX;      //没有拼图小块的空白块的坐标,x表示第几行，y表示第几列
    private int blankY;
    private int blankNumber;   // 表示空白块按照序号所在的位置，为了方便判断点击的滑块和空白方块的位置
    private int blockWidth;
    private int blockHeight;
    private Bitmap puzzlePicture;  //作为拼图的图片
    private Button start;  //方便编写对于拼图完成后的计时器停止操作,并改变计时按钮显示文字
    private Chronometer chronometer;
    private boolean isTiming;  //表示是否在计时，在计时才能移动拼图
    private List<Bitmap> picList=new ArrayList<>();   //已提供的几张图片的集合
    private Uri imageUrl;   //拍摄图片需要用到的操作
    public static final int TAKE_PHOTO=1;
    private static final int CHOOSE_PHOTO=2;
    private ImageView preview;   //存放预览图
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isTiming=false;   //初始化，表示还没有开始计时
        initBlock();    //初始化block
        puzzlePicture= BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic01);    //初始化拼图的图片来源
        puzzlePicture=puzzlePicture.createScaledBitmap(puzzlePicture,blockWidth*column,blockHeight*line,false);
        split();   //分割图片
        shuffleBlock();
        blankNumber=blankX*line+blankY;  //更新空白块的序号
        showPuzzle();  //显示puzzle

        //计时功能
        start=findViewById(R.id.start);  //计时按钮
        chronometer=findViewById(R.id.chronometer); //计时器
        String time="";
        //计时事件的响应事件
        start.setOnClickListener(new View.OnClickListener() {
            long recordingTime;
            @Override
            public void onClick(View view) {
                if(start.getText().toString().equals("开始计时")){
                    Toast t = Toast.makeText(MainActivity.this,"开始记时", Toast.LENGTH_SHORT);
                    isTiming=true;
                    t.show();
                    recordingTime=0;   //重置时间
                    // 跳过已经记录了的时间，起到继续计时的作用
                    chronometer.setBase(SystemClock.elapsedRealtime());
                    // 开始记时
                    chronometer.start();
                    start.setText("暂停计时");
                }
                else if(start.getText().toString().equals("暂停计时")){
                    Toast t = Toast.makeText(MainActivity.this,"暂停计时", Toast.LENGTH_SHORT);
                    isTiming=false;
                    t.show();
                    chronometer.stop();
                    // 保存这次记录的时间，elapsedRealtime()是开始启动到现在的毫秒数
                    recordingTime=SystemClock.elapsedRealtime()-chronometer.getBase();//getBase():返回时间
                    start.setText("继续计时");
                }
                else{      //继续计时
                    Toast t = Toast.makeText(MainActivity.this,"继续记时", Toast.LENGTH_SHORT);
                    isTiming=true;
                    t.show();
                    // 跳过已经记录了的时间，起到继续计时的作用
                    chronometer.setBase(SystemClock.elapsedRealtime()-recordingTime);
                    // 开始记时
                    chronometer.start();
                    start.setText("暂停计时");
                }
            }
        });

        //自行提供的几张图片的显示
        preview=findViewById(R.id.preview);  //预览图片的位置
        RecyclerView recyclerView=findViewById(R.id.PicRecycleView);
        LinearLayoutManager layoutManager=new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        initPicList();  //将图片加入到picList中
        preview.setImageBitmap(picList.get(0));   //默认显示第一张图片
        PicAdapter picAdapter=new PicAdapter(this,picList);
        recyclerView.setAdapter(picAdapter);

        //选定已有照片预览作为拼图原图
        Button selectBelow=findViewById(R.id.selectBelow);
        selectBelow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //将拼图换成选定的图片
                puzzlePicture=puzzlePicture.createScaledBitmap(((BitmapDrawable)preview.getDrawable()).getBitmap(),blockWidth*column,blockHeight*line,false);
                split();   //分割图片
                shuffleBlock();
                blankNumber=blankX*line+blankY;  //更新空白块的序号
                showPuzzle();
                start.setText("开始计时");   //重新开始计时
                chronometer.stop();
                isTiming=false;
            }
        });

        //设置点击“从相册中选取照片“的点击事件
        Button selectAlbum=findViewById(R.id.selectAlbum);
        selectAlbum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission((MainActivity.this),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
                }else {
                    openAlbum();
                }
            }
        });
        //设置点击“从相机中拍摄照片“的点击事件,将拍照得到的图片显示在预览处
        Button selectCamera=findViewById(R.id.selectCamera);
        selectCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File outputImage=new File(getExternalCacheDir(),"output_image.jpg");
                try {
                    if(outputImage.exists()){
                        outputImage.delete();
                    }
                    outputImage.createNewFile();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
                if(Build.VERSION.SDK_INT>=24){
                    imageUrl= FileProvider.getUriForFile(MainActivity.this,"com.example.puzzle.fileprovider",outputImage);
                }
                else{
                    imageUrl=Uri.fromFile(outputImage);
                }
                //启动相机程序
                Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT,imageUrl);
                startActivityForResult(intent,TAKE_PHOTO);
                System.out.println("aaaaaaa");
                //preview.setImageBitmap(puzzlePicture);  //设置拼图资源为相机拍摄的照片
            }
        });


    }
    //初始化block，并设置点击点击事件
    private void initBlock(){
        block[0][0]=findViewById(R.id.block0);
        block[0][1]=findViewById(R.id.block1);
        block[0][2]=findViewById(R.id.block2);
        block[1][0]=findViewById(R.id.block3);
        block[1][1]=findViewById(R.id.block4);
        block[1][2]=findViewById(R.id.block5);
        block[2][0]=findViewById(R.id.block6);
        block[2][1]=findViewById(R.id.block7);
        block[2][2]=findViewById(R.id.block8);
        block[2][3]=findViewById(R.id.block9);
        blockWidth=150;   //此处getWidth()得到的是0,???
        blockHeight=150;

        //设置点击点击事件
        block[0][0].setOnClickListener(this);
        block[0][1].setOnClickListener(this);
        block[0][2].setOnClickListener(this);
        block[1][0].setOnClickListener(this);
        block[1][1].setOnClickListener(this);
        block[1][2].setOnClickListener(this);
        block[2][0].setOnClickListener(this);
        block[2][1].setOnClickListener(this);
        block[2][2].setOnClickListener(this);
        block[2][3].setOnClickListener(this);
    }
    //分割图片，并把小碎片放入originalPic中
    private void split(){
        for (int x = 0; x < column; x++) {
            for (int y = 0; y < line; y++) {   //注意此处是需要x与y交换，不然画出来的图不符合原图的样子
                originalPic[x][y] = Bitmap.createBitmap(puzzlePicture, y * blockWidth, x * blockHeight, blockWidth, blockHeight);
            }
        }
        originalPic[line-1][column]=null;
    }
    //打乱小块拼图的顺序
    private void shuffleBlock(){
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < column * line-1; i++) {  //最后一块拼图不能参与乱序，否则拼图永远无法完成
            list.add(i);
        }
        Collections.shuffle(list);
        for (int i = 0; i < column * line-1; i++) {   //乱序之后给现在的图片位置初始化
            //currentPic[i / column][i - i / column * column]=originalPic[i / column][i - i / column * column];  //测试代码

            currentPic[i / column][i - i / column * column] = originalPic[list.get(i) / column][list.get(i) - list.get(i) / column * column];
        }

        //最后一块拼图一直显示到右边突起的角落
        currentPic[line-1][column-1]=originalPic[line-1][column];
        currentPic[line-1][column]=originalPic[line-1][column-1];
        blankX=line-1;
        blankY=column-1;
    }
    //在画面上显示出拼图小块
    private void showPuzzle(){
        for(int i=0;i<column;i++){
            for(int j=0;j<line;j++){
                //block[i][j].setImageBitmap(originalPic[i][j]);   //测试原始图片是否能正常分块显示
                block[i][j].setImageBitmap(currentPic[i][j]);   //打乱后的乱序显示
            }
        }
        block[line-1][column].setImageBitmap(currentPic[2][3]);    //别忘了特殊的这一格的显示
    }
    //初始化PicAdapter显示的图片
    private void initPicList(){
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic01));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic02));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic03));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic04));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic05));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic06));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic07));
        picList.add(BitmapFactory.decodeResource(this.getResources(), R.mipmap.pic08));
    }
    //给各个控件设置点击事件
    @Override
    public void onClick(View v){
        int location=0;
        if(isTiming){  //只有在计时状态才能移动拼图
            switch(v.getId()){
                case R.id.block0:   //右下
                    location=0;
                    if(blankNumber-location==1){    //空白块在滑块的右边
                        goRight(location);
                    }
                    else if(blankNumber-location==line){  //空白块在滑块的下面
                        goDown(location);
                    }
                    break;
                case R.id.block1:  //左右下
                    location=1;
                    if(blankNumber-location==1){    //空白块在滑块的右边
                        goRight(location);
                    }
                    else if(blankNumber-location==line){  //空白块在滑块的下面
                        goDown(location);
                    }
                    else if(location-blankNumber==1){  //空白块在滑块的左边
                        goLeft(location);
                    }
                    break;
                case R.id.block2:   //左下
                    location=2;
                    if(blankNumber-location==line){  //空白块在滑块的下面
                        goDown(location);
                    }
                    else if(location-blankNumber==1){  //空白块在滑块的左边
                        goLeft(location);
                    }
                    break;
                case R.id.block3:    //上下右
                    location=3;
                    if(blankNumber-location==1){    //空白块在滑块的右边
                        goRight(location);
                    }
                    else if(blankNumber-location==line){  //空白块在滑块的下面
                        goDown(location);
                    }
                    else if(location-blankNumber==line){  //空白块在滑块的上
                        goUp(location);
                    }
                    break;
                case R.id.block4:   //上下左右
                    location=4;
                    if(location-blankNumber==line){   //上
                        goUp(location);
                    }
                    else if(blankNumber-location==line){  //下
                        goDown(location);
                    }
                    else if(location-blankNumber==1){  //左
                        goLeft(location);
                    }
                    else if(blankNumber-location==1){  //右
                        goRight(location);
                    }
                    break;
                case R.id.block5:
                    location=5;
                    if(location-blankNumber==line){   //上
                        goUp(location);
                    }
                    else if(blankNumber-location==line){  //下
                        goDown(location);
                    }
                    else if(location-blankNumber==1){  //左
                        goLeft(location);
                    }
                    break;
                case R.id.block6:
                    location=6;
                    if(location-blankNumber==line){   //上
                        goUp(location);
                    }
                    else if(blankNumber-location==1){  //右
                        goRight(location);
                    }
                    break;
                case R.id.block7:
                    location=7;
                    if(location-blankNumber==line){   //上
                        goUp(location);
                    }
                    else if(location-blankNumber==1){  //左
                        goLeft(location);
                    }
                    else if(blankNumber-location==1){  //右
                        goRight(location);
                    }
                    break;
                case R.id.block8:
                    location=8;
                    if(location-blankNumber==line){   //上
                        goUp(location);
                    }
                    else if(location-blankNumber==1){  //左
                        goLeft(location);
                    }
                    else if(blankNumber-location==1){  //右
                        goRight(location);
                    }
                    break;
                case R.id.block9:
                    System.out.println("点击9方块");
                    location=9;
                    if(location-blankNumber==1){  //左
                        goLeft(location);
                    }
                    break;
                default:
                    break;
            }
        }
        else{
            if(v.getId()==R.id.block0
                    ||v.getId()==R.id.block1
                    ||v.getId()==R.id.block2
                    ||v.getId()==R.id.block3
                    ||v.getId()==R.id.block4
                    ||v.getId()==R.id.block5
                    ||v.getId()==R.id.block6
                    ||v.getId()==R.id.block7
                    ||v.getId()==R.id.block8
                    ||v.getId()==R.id.block9
            ){
                Toast t = Toast.makeText(MainActivity.this,"未计时状态，不可移动拼图", Toast.LENGTH_SHORT);
                t.show();
            }

        }
    }

    //块from上的图片往右移动
    private void goRight(int from){
        int x,y;
        //坐标的计算
        if(from/column==line){
            x=line-1;
            y=column;
        }
        else{
            x=from/column;
            y=from-from/column*column;
        }
        //往右移动一格的动画对象
        TranslateAnimation animation=new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f ,
                Animation.RELATIVE_TO_SELF,1.0f,
                Animation.RELATIVE_TO_SELF,0.0f,
                Animation.RELATIVE_TO_SELF,0.0f);
        animation.setDuration(500);
        block[x][y].startAnimation(animation);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                block[x][y].clearAnimation();
                blankY-=1;
                blankNumber=blankX*line+blankY;  //时刻都要更新空白块位置
                block[x][y+1].setImageBitmap(currentPic[x][y]);
                block[x][y].setImageBitmap(null);
                currentPic[x][y+1]=currentPic[x][y];
                currentPic[x][y]=null;
                block[x][y].setImageBitmap(null);
                //System.out.println("blankX: "+blankX);
                //System.out.println("blankY: "+blankY);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }
    //块from上的图片往左移动
    private void goLeft(int from){
        int x,y;
        if(from/column==line){
            x=line-1;
            y=column;
        }
        else{
            x=from/column;
            y=from-from/column*column;
        }
        //往右移动一格的动画对象
        TranslateAnimation animation=new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f ,
                Animation.RELATIVE_TO_SELF,-1.0f,
                Animation.RELATIVE_TO_SELF,0.0f,
                Animation.RELATIVE_TO_SELF,0.0f);
        animation.setDuration(500);
        block[x][y].startAnimation(animation);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                block[x][y].clearAnimation();
                blankY+=1;
                blankNumber=blankX*line+blankY;  //时刻都要更新空白块位置
                block[x][y-1].setImageBitmap(currentPic[x][y]);
                block[x][y].setImageBitmap(null);
                currentPic[x][y-1]=currentPic[x][y];
                currentPic[x][y]=null;
                block[x][y].setImageBitmap(null);
                //System.out.println("blankX: "+blankX);
                //System.out.println("blankY: "+blankY);
                if(x==line-1&&y==column){     //如果是最后一格进行往左移动，就判断是否完成拼图
                    if(whetherComplete()){
                        chronometer.stop();
                        Toast t = Toast.makeText(MainActivity.this,"恭喜完成拼图，用时"+chronometer.getText().toString(), Toast.LENGTH_SHORT);
                        t.show();
                        start.setText("开始计时");
                        isTiming=false;   //没有开始计时
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }
    //块from上的图片往上移动
    private void goUp(int from){
        int x,y;
        if(from/column==line){
            x=line-1;
            y=column;
        }
        else{
            x=from/column;
            y=from-from/column*column;
        }
        //往右移动一格的动画对象
        TranslateAnimation animation=new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f ,
                Animation.RELATIVE_TO_SELF,0.0f,
                Animation.RELATIVE_TO_SELF,0.0f,
                Animation.RELATIVE_TO_SELF,-1.0f);
        animation.setDuration(500);
        block[x][y].startAnimation(animation);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                block[x][y].clearAnimation();
                blankX+=1;
                blankNumber=blankX*line+blankY;  //时刻都要更新空白块位置
                block[x-1][y].setImageBitmap(currentPic[x][y]);
                block[x][y].setImageBitmap(null);
                currentPic[x-1][y]=currentPic[x][y];
                currentPic[x][y]=null;
                block[x][y].setImageBitmap(null);
                //.out.println("blankX: "+blankX);
                //System.out.println("blankY: "+blankY);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }
    //块from上的图片往下移动
    private void goDown(int from){
        int x,y;
        if(from/column==line){
            x=line-1;
            y=column;
        }
        else{
            x=from/column;
            y=from-from/column*column;
        }
        //往右移动一格的动画对象
        TranslateAnimation animation=new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f ,
                Animation.RELATIVE_TO_SELF,0.0f,
                Animation.RELATIVE_TO_SELF,0.0f,
                Animation.RELATIVE_TO_SELF,1.0f);
        animation.setDuration(500);
        block[x][y].startAnimation(animation);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                block[x][y].clearAnimation();
                blankX-=1;
                blankNumber=blankX*line+blankY;  //时刻都要更新空白块位置
                block[x+1][y].setImageBitmap(currentPic[x][y]);
                block[x][y].setImageBitmap(null);
                currentPic[x+1][y]=currentPic[x][y];
                currentPic[x][y]=null;
                block[x][y].setImageBitmap(null);
                //.out.println("blankX: "+blankX);
                //System.out.println("blankY: "+blankY);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }
    //判断是否已经拼图成功
    private boolean whetherComplete(){   //判断是否已经拼图成功
        for(int i=0;i<line;i++){
            for(int j=0;j<column;j++){
                if(currentPic[i][j]!=originalPic[i][j]){
                    return false;
                }
            }
        }
        return true;
    }


    //相机中拍摄照片作为拼图原图
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case TAKE_PHOTO:  //从相机中寻找照片
                if (resultCode == RESULT_OK) {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUrl));
                        preview.setImageBitmap(bitmap);  //设置预览图为相机拍摄的照片
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case CHOOSE_PHOTO:  //从相册中选择
                if(resultCode==RESULT_OK){
                    if(Build.VERSION.SDK_INT>=19){
                        //4.4及以上系统使用这个方法处理照片
                        handleImageOnBitKat(data);
                    }
                    else{
                        handleImageBeforeKitKat(data);
                    }
                }
                break;
            default:
                break;
        }
    }
    //以下是从相册中选取图片的功能实现，参考《第一行代码》
    private void openAlbum(){
        Intent intent =new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO);  //打开相册
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permission,int[] grantResults){
        switch (requestCode){
            case 1:
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    openAlbum();
                } else{
                    Toast.makeText(this,"You denied the permission",Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }
    @TargetApi(19)
    private void handleImageOnBitKat(Intent data){
        String imagePath=null;
        Uri uri=data.getData();
        if(DocumentsContract.isDocumentUri(this,uri)){
            //如果是document类型的Uri，则通过document id处理
            String docId=DocumentsContract.getDocumentId(uri);
            if("com.android.providers.media.documents".equals(uri.getAuthority())){
                String id=docId.split(":")[1];  //解析出数字格式的id
                String selection =MediaStore.Images.Media._ID+"="+id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,selection);
            }else if("com.android.providers.downloads.documents".equals(uri.getAuthority())){
                Uri contentUri= ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),Long.valueOf(docId));
                imagePath=getImagePath(contentUri,null);
            }
        }else if("content".equalsIgnoreCase(uri.getScheme())){
            //如果是content类别的Uri，则用普通方式处理
            imagePath=getImagePath(uri,null);
        }else if("file".equalsIgnoreCase(uri.getScheme())){
            //如果是file类型的Uri，直接获取图片路径即可
            imagePath=uri.getPath();
        }
        displayImage(imagePath);
    }
    private void handleImageBeforeKitKat(Intent data){
        Uri uri=data.getData();
        String imagePath=getImagePath(uri,null);
        displayImage(imagePath);
    }
    @SuppressLint("Range")
    private String getImagePath(Uri uri, String selection){
        String path=null;
        //通过Uri和selection来获取真实的图片路径
        Cursor cursor=getContentResolver().query(uri,null,selection,null,null);
        if(cursor!=null){
            if(cursor.moveToFirst()){
                path=cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }
    private void displayImage(String imagePath){
        if(imagePath!=null){
            Bitmap bitmap=BitmapFactory.decodeFile(imagePath);
            preview.setImageBitmap(bitmap);
        } else{
            Toast.makeText(this,"failed to get image",Toast.LENGTH_SHORT).show();
        }
    }

}