package com.kevin.rfidmanager.Adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.kevin.rfidmanager.Activity.GalleryActivity;
import com.kevin.rfidmanager.MyApplication;
import com.kevin.rfidmanager.R;
import com.kevin.rfidmanager.Utils.ConstantManager;
import com.kevin.rfidmanager.Utils.DatabaseUtil;
import com.kevin.rfidmanager.Utils.ScreenUtil;
import com.kevin.rfidmanager.database.DaoSession;
import com.kevin.rfidmanager.database.ImagesPath;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.List;

import at.markushi.ui.CircleButton;

import static com.kevin.rfidmanager.Utils.ConstantManager.DEFAULT_IMAGE_HEIGHT_DP;

/**
 * Created by Kevin on 2017/1/29.
 * Mail: chewenkaich@gmail.com
 */

public class GallaryAdaper extends RecyclerView.Adapter<GallaryAdaper.ViewHolder> {
    public Activity activity;
    private List<ImagesPath> paths;
    private boolean hide;
    private String currentID = ConstantManager.DEFAULT_RFID;

    public GallaryAdaper(Activity activity, List<ImagesPath> paths, boolean hide, String currentID) {
        this.activity = activity;
        this.paths = paths;
        this.hide = hide;
        this.currentID = currentID;
    }

    public Context getContext() {
        return activity.getApplicationContext();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate the custom layout
        View contactView = inflater.inflate(R.layout.gallary_layout, parent, false);

        // Return a new holder instance
        return new ViewHolder(contactView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        // Get the data model based on position
        final ImagesPath path = paths.get(position);

        // Set item views based on your views and data model
        final ImageView image = holder.image;
        image.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    // Wait until layout to call Picasso
                    @Override
                    public void onGlobalLayout() {
                        // Ensure we call this only once
                        image.getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                    }
                });
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Picasso.with(activity).load(new File(path.getImagePath())).resize(0,
                    ScreenUtil.dpToPx(activity, DEFAULT_IMAGE_HEIGHT_DP)).into(image);
        } else {
            Picasso.with(activity).load(R.drawable.image_read_fail).resizeDimen(0,
                    ScreenUtil.dpToPx(activity, DEFAULT_IMAGE_HEIGHT_DP)).into(image);
        }
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(ConstantManager.GALLERY_CLICK_POSITION, holder.getAdapterPosition());
                intent.putExtra(ConstantManager.CURRENT_ITEM_ID, currentID);
                intent.setClass(activity, GalleryActivity.class);
                activity.startActivity(intent);
            }
        });
        CircleButton button = holder.removeButton;
        if (hide){
            button.setVisibility(View.GONE);
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DaoSession daoSession = ((MyApplication) activity.getApplicationContext()).getDaoSession();
                daoSession.getImagesPathDao().delete(path);
                updateUI();
            }
        });
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    public void updateUI() {
        this.paths.clear();
        this.paths.addAll(DatabaseUtil.queryImagesPaths(activity, currentID));
        this.notifyDataSetChanged();
    }

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    static class ViewHolder extends RecyclerView.ViewHolder {
        // Your holder should contain a member variable
        // for any view that will be set as you render a row
        public ImageView image;
        CircleButton removeButton;

        // We also create a constructor that accepts the entire item row
        // and does the view lookups to find each subview
        ViewHolder(View itemView) {
            // Stores the itemView in a public final member variable that can be used
            // to access the context from any ViewHolder instance.
            super(itemView);

            image = (ImageView) itemView.findViewById(R.id.gallary_image);
            removeButton = (CircleButton) itemView.findViewById(R.id.remove_gallary_button);
        }
    }

    public void getPermmision() {
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat
                .checkSelfPermission(activity,
                        Manifest.permission.MANAGE_DOCUMENTS)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale
                            (activity, Manifest.permission.MANAGE_DOCUMENTS)) {

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission
                                .WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_CONTACTS},
                        100);

            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission
                                .WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_CONTACTS},
                        100);
            }
        } else {
            //Call whatever you want
//            myMethod();
        }

    }

}
