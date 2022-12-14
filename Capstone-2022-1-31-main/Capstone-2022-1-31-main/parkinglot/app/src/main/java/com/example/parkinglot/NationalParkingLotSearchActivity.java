package com.example.parkinglot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinglot.entity.ParkingLotData;
import com.example.parkinglot.entity.ParkingLotItem;
import com.example.parkinglot.entity.ParkingLotSingleData;
import com.example.parkinglot.entity.Point;
import com.example.parkinglot.entity.SingleCheckData;
import com.example.parkinglot.util.Constants;
import com.example.parkinglot.util.GlobalVariable;
import com.example.parkinglot.util.OnItemClickListener;
import com.example.parkinglot.util.ParkingLotAdapter;
import com.example.parkinglot.util.Utils;
import com.google.gson.Gson;
import com.skt.Tmap.TMapData;
import com.skt.Tmap.TMapPolyLine;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import fr.arnaudguyon.xmltojsonlib.XmlToJson;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NationalParkingLotSearchActivity extends AppCompatActivity {
    private static final String TAG = NationalParkingLotSearchActivity.class.getSimpleName();

    // ?????? ????????????, ????????? ????????? ????????? ????????????
    private LinearLayout layLoading, layNoData;

    private RecyclerView recyclerView;
    private ParkingLotAdapter adapter;
    private ArrayList<ParkingLotItem> items;

    private Spinner spSection, spType, spChargeInfo;
    private EditText editKeyword;

    private InputMethodManager imm;                 // ???????????? ????????? ?????? ?????????

    private double latitude, longitude;             // ?????? ?????? / ??????

    private int page;                               // ?????????
    private int dataCount;                          // ????????? ???

    private static final String API_URL = "http://api.data.go.kr/openapi/tn_pubr_prkplce_info_api"; // ????????? api url
    private static final int ITEM_PAGE_SIZE =  500;             // ???????????? ????????? ??? (open api ????????? ?????? ????????? ???)

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_national_parking_lot_search);

        // ?????? ?????? / ?????? ??????
        Intent intent = getIntent();
        this.latitude = intent.getDoubleExtra("latitude", 0);
        this.longitude = intent.getDoubleExtra("longitude", 0);

        // ?????? ??????
        setTitle(getString(R.string.title_parking_lot_search));

        // ?????????(<-) ??????
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // ?????? ????????????
        this.layLoading = findViewById(R.id.layLoading);
        ((ProgressBar) findViewById(R.id.progressBar)).setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));

        // ????????? ????????? ????????? ????????????
        this.layNoData = findViewById(R.id.layNoData);

        this.recyclerView = findViewById(R.id.recyclerView);
        this.recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        this.editKeyword = findViewById(R.id.editKeyword);
        this.editKeyword.setImeOptions(EditorInfo.IME_ACTION_DONE);
        this.editKeyword.setHint("???????????? ?????? ??????");

        this.spSection = findViewById(R.id.spSection);
        this.spType = findViewById(R.id.spType);
        this.spChargeInfo = findViewById(R.id.spChargeInfo);

        // ??????????????? spinner ??????
        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(this, R.layout.spinner_item,
                getResources().getStringArray(R.array.parking_lot_section));
        adapter1.setDropDownViewResource(R.layout.spinner_dropdown_item);
        this.spSection.setAdapter(adapter1);

        // ??????????????? spinner ??????
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, R.layout.spinner_item,
                getResources().getStringArray(R.array.parking_lot_type));
        adapter2.setDropDownViewResource(R.layout.spinner_dropdown_item);
        this.spType.setAdapter(adapter2);

        // ??????????????? spinner ??????
        ArrayAdapter<String> adapter3 = new ArrayAdapter<>(this, R.layout.spinner_item,
                getResources().getStringArray(R.array.parking_charge_info));
        adapter3.setDropDownViewResource(R.layout.spinner_dropdown_item);
        this.spChargeInfo.setAdapter(adapter3);

        findViewById(R.id.btnSearch).setOnClickListener(view -> {
            // ????????? ?????? ??????
            String keyword = this.editKeyword.getText().toString();
            if (TextUtils.isEmpty(keyword)) {
                Toast.makeText(this, R.string.msg_parking_lot_check_empty, Toast.LENGTH_SHORT).show();
                this.editKeyword.requestFocus();
                return;
            }

            // ????????? ????????? ??????
            if (keyword.length() < 2) {
                Toast.makeText(this, R.string.msg_parking_lot_check_empty, Toast.LENGTH_SHORT).show();
                this.editKeyword.requestFocus();
                return;
            }

            // ????????? ?????????
            this.imm.hideSoftInputFromWindow(this.editKeyword.getWindowToken(), 0);

            this.layNoData.setVisibility(View.GONE);

            // ?????? ???????????? ??????
            this.layLoading.setVisibility(View.VISIBLE);

            // ??????????????????  ???????????? ??????
            new Handler(Looper.getMainLooper()).post(() -> {
                // ArrayList ?????????
                this.items = new ArrayList<>();

                boolean download = false;
                if (GlobalVariable.parkingLotItems != null) {
                    if (GlobalVariable.parkingLotItems.size() > 0) {
                        download = true;
                    }
                }

                if (download) {
                    // ?????? ????????? ????????? ??????????????????
                    searchParkingLot();
                } else {
                    GlobalVariable.parkingLotItems = new ArrayList<>();

                    this.page = 1;
                    this.dataCount = 0;

                    // ?????? api ??????
                    callOpenApi();
                }
            });
        });

        findViewById(R.id.layLoading).setOnClickListener(view -> {
            // ?????? ??????
        });

        // ???????????? ????????? ?????? ?????????
        this.imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        this.editKeyword.requestFocus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* ????????? ??? ???????????? ?????? */
    private void searchParkingLot() {
        Log.d(TAG, "download total:" +  GlobalVariable.parkingLotItems.size());

        String keyword = this.editKeyword.getText().toString();
        // ???????????? ?????????
        if (!TextUtils.isEmpty(keyword)) {
            for (ParkingLotItem item : GlobalVariable.parkingLotItems) {
                boolean exist = true;

                // ???????????????
                if (this.spSection.getSelectedItemPosition() > 0) {
                    exist = item.prkplceSe.equals(this.spSection.getSelectedItem().toString());
                }

                if (exist) {
                    // ???????????????
                    if (this.spType.getSelectedItemPosition() > 0) {
                        exist = item.prkplceType.equals(this.spType.getSelectedItem().toString());
                    }
                }

                if (exist) {
                    // ????????????
                    if (this.spChargeInfo.getSelectedItemPosition() > 0) {
                        exist = item.parkingchrgeInfo.equals(this.spChargeInfo.getSelectedItem().toString());
                    }
                }

                if (exist) {
                    exist = false;

                    // ????????? ????????? ???????????? ?????? ?????? ????????? ??????
                    if (item.prkplceNm.contains(keyword)) {
                        exist = true;
                    } else {
                        // ????????? ????????? ?????????
                        if (!TextUtils.isEmpty(item.rdnmadr)) {
                            // ????????? ?????? ?????? ????????? ??????
                            if (item.rdnmadr.contains(keyword)) {
                                exist = true;
                            } else {
                                // ?????? ????????? ?????????
                                if (!TextUtils.isEmpty(item.lnmadr)) {
                                    // ????????? ?????? ?????? ????????? ??????
                                    if (item.lnmadr.contains(keyword)) {
                                        exist = true;
                                    }
                                }
                            }
                        }
                    }
                }

                // ?????? item ??????
                if (exist) {
                    // ????????? ?????? ??????
                    if (Utils.isNumeric(item.latitude) && Utils.isNumeric(item.longitude)) {
                        item.distance = Utils.getDistance(latitude, longitude,
                                Double.parseDouble(item.latitude), Double.parseDouble(item.longitude));
                    } else {
                        // ?????? ?????? ??????
                        item.distance = Constants.NO_DISTANCE;
                    }

                    items.add(item);
                }
            }
        }

        complete();
    }

    /* ????????? ????????? ?????? Comparator (?????? ASC) */
    private Comparator<ParkingLotItem> getComparator() {
        Comparator<ParkingLotItem> comparator = (sort1, sort2) -> {
            // ??????
            return Double.compare(sort1.distance, sort2.distance);
        };

        return comparator;
    }

    /* ?????? ?????? */
    private void complete() {
        // ?????? ???????????? ??????
        this.layLoading.setVisibility(View.GONE);

        if (this.items.size() == 0) {
            this.layNoData.setVisibility(View.VISIBLE);
        } else {
            // ??????????????? ??????
            Collections.sort(this.items, getComparator());
        }

        // ????????? ??????
        this.adapter = new ParkingLotAdapter(mItemClickListener, this.items, this.latitude, this.longitude);
        this.recyclerView.setAdapter(this.adapter);
    }

    /* Open api ?????? (?????????) */
    private void callOpenApi() {
        try {
            // ?????? api ??????
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            String url = API_URL;
            url += "?serviceKey=" + getString(R.string.open_api_key);       // ?????????
            //url += "&prkplceNm=" + URLEncoder.encode(this.editKeyword.getText().toString(), "UTF-8");   // ????????????
            //url += "&prkplceSe=" + URLEncoder.encode("??????", "UTF-8");   // ???????????????
            //url += "&prkplceType=" + URLEncoder.encode("??????", "UTF-8");   // ???????????????
            //url += "&prkplceSe=" + URLEncoder.encode("??????", "UTF-8");   // ????????????
            // json ????????? ????????? ????????? ??????, (json ????????? ????????? ?????? ?????? item ??? ?????? ?????????)
            //url += "&type=" + "json";                                     // xml / json
            url += "&pageNo=" + this.page;                                  // ?????????
            url += "&numOfRows=" + ITEM_PAGE_SIZE;                          // ?????? ????????? ???

            Log.d(TAG, "url:" + url);

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            okHttpClient.newCall(request).enqueue(mCallback);
        } catch (Exception e) {
            // Error
            Toast.makeText(this, R.string.msg_error, Toast.LENGTH_SHORT).show();
        }
    }

    /* ????????? ?????? ????????? Callback */
    private final Callback mCallback = new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            Log.d(TAG, "????????????:" + e.getMessage());

            new Handler(Looper.getMainLooper()).post(() -> {
                // ?????? ???????????? ??????
                layLoading.setVisibility(View.GONE);
                Toast.makeText(NationalParkingLotSearchActivity.this, R.string.msg_error, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            final String responseData = response.body().string();

            if (!TextUtils.isEmpty(responseData)) {
                //Log.d(TAG, "???????????? ????????? ?????????:" + responseData);

                new Handler(Looper.getMainLooper()).post(() -> {
                    // XML ??? JSON ?????? ??????
                    XmlToJson xmlToJson = new XmlToJson.Builder(responseData).build();
                    String json = xmlToJson.toString();
                    Log.d(TAG, "json:" + json);

                    if (json.contains("{\"response\":{\"header\":{\"code\":")) {
                        // ??????
                        complete();
                        return;
                    }

                    // JSON to Object
                    Gson gson = new Gson();

                    // ???????????? ?????? (xml ????????? ???????????? 1??? ???????????? 2????????????????????? ????????? ????????? ????????? ?????? ????????? ?????? ???)
                    SingleCheckData checkData = gson.fromJson(json, SingleCheckData.class);

                    if (checkData.response.header.resultCode.equals("00")) {
                        // ??????

                        // ????????? ??????
                        int pageNo = Integer.parseInt(checkData.response.body.pageNo);
                        // ???????????? ????????? ???
                        int numOfRows = Integer.parseInt(checkData.response.body.numOfRows);
                        // ??? ?????????
                        int totalCount = Integer.parseInt(checkData.response.body.totalCount);

                        Log.d(TAG, "totalCount:" + totalCount);

                        if (totalCount > 0) {
                            if ((totalCount - ((pageNo - 1) * numOfRows)) == 1) {
                                // ?????? ?????????
                                ParkingLotSingleData singleData = gson.fromJson(json, ParkingLotSingleData.class);

                                // ?????? ????????? array ??????
                                GlobalVariable.parkingLotItems.add(singleData.response.body.items.item);

                                // ???????????? ?????? ????????????
                                dataCount ++;
                            } else {

                                // ?????? ?????????
                                // json ?????? ???????????? ????????? ?????? ????????? ?????????
                                ParkingLotData data = gson.fromJson(json, ParkingLotData.class);

                                // ?????? ????????? array ??????
                                GlobalVariable.parkingLotItems.addAll(data.response.body.items.item);

                                // ???????????? ?????? ????????????
                                dataCount += data.response.body.items.item.size();
                            }

                            if (totalCount > dataCount) {
                                // ?????? ???????????? ??? ?????????
                                page++;

                                // ?????? api ?????? (?????? ?????????)
                                callOpenApi();
                            } else {
                                // ????????? ??? ???????????? ??????
                                searchParkingLot();
                            }
                        } else {
                            // ????????? ??? ???????????? ??????
                            searchParkingLot();
                        }
                    } else {
                        // ??????
                        complete();
                        Toast.makeText(NationalParkingLotSearchActivity.this, checkData.response.header.resultMsg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    };

    private final OnItemClickListener mItemClickListener = (view, position) -> {
        // ??????
        GlobalVariable.parkingLotItem = this.items.get(position);

        // ????????? ?????? Activity
        Intent intent = new Intent(this, ParkingLotInfoActivity.class);
        intent.putExtra("position", position);
        this.parkingLotActivityLauncher.launch(intent);
    };

    /* ??????????????? ActivityForResult */
    private final ActivityResultLauncher<Intent> parkingLotActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // ?????????
                    Intent data = result.getData();
                    if (data != null) {
                        int position = data.getIntExtra("position", -1);
                        if (position == -1) {
                            return;
                        }

                        double lat, lng;
                        ParkingLotItem item = this.items.get(position);
                        if (Utils.isNumeric(item.latitude) && Utils.isNumeric(item.longitude)) {
                            lat = Double.parseDouble(item.latitude);
                            lng = Double.parseDouble(item.longitude);
                        } else {
                            // ????????? ?????? / ?????? ????????????
                            Point point = null;
                            if (!TextUtils.isEmpty(item.rdnmadr)) {
                                point = Utils.getGpsFromAddress(this, item.rdnmadr);
                            } else {
                                if (!TextUtils.isEmpty(item.lnmadr)) {
                                    point = Utils.getGpsFromAddress(this, item.lnmadr);
                                }
                            }

                            if (point != null) {
                                lat = point.latitude;
                                lng = point.longitude;
                            } else {
                                // ??????????????? ??????
                                Toast.makeText(this, R.string.msg_parking_lot_location_empty, Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        // Activity ??? ??????
                        Intent intent = new Intent();
                        intent.putExtra("parking_no", item.prkplceNo);
                        intent.putExtra("parking_lot", item.prkplceNm);
                        intent.putExtra("latitude", lat);
                        intent.putExtra("longitude", lng);
                        intent.putExtra("save", true);
                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    }
                }
            });
}