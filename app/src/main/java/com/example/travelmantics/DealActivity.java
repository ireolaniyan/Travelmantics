package com.example.travelmantics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.travelmantics.Models.TravelDeal;
import com.example.travelmantics.Utils.FirebaseUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

public class DealActivity extends AppCompatActivity {
    public static final int PICTURE_RESULT = 42;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;
    private EditText textTitle;
    private EditText textPrice;
    private EditText textDescription;
    private TravelDeal deal;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal);

//        FirebaseUtil.openFbReference("traveldeals", this);
        mFirebaseDatabase = FirebaseUtil.mFirebaseDatabase;
        mDatabaseReference = FirebaseUtil.mDatabaseReference;

        textTitle = findViewById(R.id.edit_text_title);
        textDescription = findViewById(R.id.edit_text_description);
        textPrice = findViewById(R.id.edit_text_price);
        imageView = findViewById(R.id.image);
        Button buttonUploadImage = findViewById(R.id.button_upload_image);
        buttonUploadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(intent.createChooser(intent, "Insert Picture"), PICTURE_RESULT);
            }
        });

        Intent intent = getIntent();
        TravelDeal deal = (TravelDeal) intent.getSerializableExtra("Deal");
        if (deal == null) {
            deal = new TravelDeal();
        }
        this.deal = deal;

        textTitle.setText(deal.getTitle());
        textDescription.setText(deal.getDescription());
        textPrice.setText(deal.getPrice());
        showImage(deal.getImageUrl());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICTURE_RESULT && resultCode == RESULT_OK) {
            Uri imageUri = data.getData();
            StorageReference ref = FirebaseUtil.mStorageReference.child(imageUri.getLastPathSegment());
            ref.putFile(imageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Task<Uri> firebaseUri = taskSnapshot.getStorage().getDownloadUrl();
                    firebaseUri.addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            String url = uri.toString();
                            deal.setImageUrl(url);
                            showImage(url);
                            Log.d("Url: ", url);
                        }
                    });
                    String pictureName = taskSnapshot.getStorage().getPath();
                    deal.setImageName(pictureName);
                }
            });
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        Show Save Menu
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.save_menu, menu);

        if (FirebaseUtil.isAdmin) {
            menu.findItem(R.id.delete_menu).setVisible(true);
            menu.findItem(R.id.save_menu).setVisible(true);
            enableEditTexts(true);
            findViewById(R.id.button_upload_image).setEnabled(true);
        } else {
            menu.findItem(R.id.delete_menu).setVisible(false);
            menu.findItem(R.id.save_menu).setVisible(false);
            enableEditTexts(false);
            findViewById(R.id.button_upload_image).setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_menu:
                saveDeal();
                Toast.makeText(this, "Deal saved", Toast.LENGTH_LONG).show();
                cleanEditText();
                backToList();
                return true;

            case R.id.delete_menu:
                deleteDeal();
                Toast.makeText(this, "Deal deleted", Toast.LENGTH_LONG).show();
                backToList();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void cleanEditText() {
        textTitle.setText("");
        textPrice.setText("");
        textDescription.setText("");
        textTitle.requestFocus();
    }

    private void saveDeal() {
        deal.setTitle(textTitle.getText().toString());
        deal.setDescription(textDescription.getText().toString());
        deal.setPrice(textPrice.getText().toString());

        if (deal.getId() == null) {
            mDatabaseReference.push().setValue(deal);
        } else {
            mDatabaseReference.child(deal.getId()).setValue(deal);
        }
    }

    private void deleteDeal() {
        if (deal == null) {
            Toast.makeText(this, "Please save the note before you delete it", Toast.LENGTH_LONG).show();
            return;
        }
        mDatabaseReference.child(deal.getId()).removeValue();

        if (deal.getImageName() != null && !deal.getImageName().isEmpty()) {
            StorageReference picRef = FirebaseUtil.mFirebaseStorage.getReference().child(deal.getImageName());
            picRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d("Delete Image: ", "Image successfully deleted");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d("Delete Image: ", e.getMessage());
                }
            });
        }
    }

    private void backToList() {
        Intent intent = new Intent(this, ListActivity.class);
        startActivity(intent);
    }


    private void enableEditTexts(boolean isEnabled) {
        textTitle.setEnabled(isEnabled);
        textDescription.setEnabled(isEnabled);
        textPrice.setEnabled(isEnabled);
    }

    private void showImage(String url) {
        if (url != null && !url.isEmpty()) {
            int width = Resources.getSystem().getDisplayMetrics().widthPixels;
            Picasso.with(this)
                    .load(url)
                    .resize(width, width * 2/3)
                    .centerCrop()
                    .into(imageView);
        }
    }

}

