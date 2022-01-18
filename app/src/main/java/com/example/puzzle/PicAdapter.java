package com.example.puzzle;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PicAdapter extends RecyclerView.Adapter<PicAdapter.ViewHolder> {
    private List<Bitmap> picList;
    private Activity main_activity;
    static class ViewHolder extends RecyclerView.ViewHolder{
        ImageView picImage;
        public ViewHolder(View view){
            super(view);
            picImage=view.findViewById(R.id.pic_image);
        }
    }
    public PicAdapter(Activity ac,List<Bitmap> mpicList){
        main_activity=ac;
        picList=mpicList;
    }
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType){
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.pic_item,parent,false);
        ViewHolder holder=new ViewHolder(view);
        ImageView preview=main_activity.findViewById(R.id.preview);  //预览所选图片的imageView
        holder.itemView.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                int position =holder.getAdapterPosition();
                Bitmap pic =picList.get(position);
                preview.setImageBitmap(pic);
            }
        });
        return holder;
    }
    @Override
    public void onBindViewHolder(ViewHolder holder, int position){
        Bitmap pic=picList.get(position);
        holder.picImage.setImageBitmap(pic);
    }

    @Override
    public int getItemCount(){
        return picList.size();
    }
}
