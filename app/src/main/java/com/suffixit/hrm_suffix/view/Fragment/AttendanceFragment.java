/*package com.suffixit.hrm_suffix.view.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.suffixit.hrm_suffix.Adapter.AttendanceAdapter;
import com.suffixit.hrm_suffix.R;
import com.suffixit.hrm_suffix.databinding.FragmentAttendanceBinding;
import com.suffixit.hrm_suffix.models.AttendanceModel;
import com.suffixit.hrm_suffix.view.Activities.MainActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceFragment extends Fragment {
    private FragmentAttendanceBinding binding;
    private DatabaseReference databaseReference;
    private RecyclerView recyclerView;
    private AttendanceAdapter attendanceAdapter;
    private List<AttendanceModel> employeeList;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_KEY_CHECKIN_TIME = "checkin_time";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAttendanceBinding.inflate(inflater, container, false);

        setUpOnBackPressed();
        recyclerView = binding.recyclerViewId;
        employeeList = new ArrayList<>();

        attendanceAdapter = new AttendanceAdapter(getActivity(), employeeList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(attendanceAdapter);

        // Retrieve the saved check-in time from SharedPreferences
        sharedPreferences = requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String savedCheckInTime = sharedPreferences.getString(PREFS_KEY_CHECKIN_TIME, "");

        if (!savedCheckInTime.isEmpty()) {
            // If a check-in time is saved, update the UI accordingly
            binding.cardViewCheckIn.setVisibility(View.GONE);
            binding.cardViewCheckout.setVisibility(View.VISIBLE);
            binding.savedCheckInTimeTextView.setText("Check-in time: " + savedCheckInTime);
        }

        databaseReference = FirebaseDatabase.getInstance().getReference().child("Check-ins");
        fetchDataFromFirebase();
        binding.cardViewCheckIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Save the check-in time
                String checkInTime = getCurrentTime();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PREFS_KEY_CHECKIN_TIME, checkInTime);
                editor.apply();

                binding.cardViewCheckIn.setVisibility(View.GONE);
                binding.cardViewCheckout.setVisibility(View.VISIBLE);
                binding.savedCheckInTimeTextView.setText("Check-in time: " + checkInTime);
                String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String currentDay = new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date());
                String checkoutTime = "00.00";
                String totalHrs= "00.00";

                AttendanceModel checkIn = new AttendanceModel(currentDate, currentDay, checkInTime, checkoutTime, totalHrs);
                employeeList.add(checkIn);
                attendanceAdapter.notifyDataSetChanged();
                databaseReference.push().setValue(checkIn)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                //    Toast.makeText(getActivity(), "Check-in successful", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(getActivity(), "Failed to check in", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });


        binding.cardViewCheckout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String checkInTime = sharedPreferences.getString(PREFS_KEY_CHECKIN_TIME, "");

                if (!checkInTime.isEmpty()) {
                    // Update the existing check-in entry with checkout time
                    String checkoutTime = getCurrentTime();

                    // Calculate total hours
                    double totalHrs = calculateTotalHours(checkInTime, checkoutTime);
                    String totalHrsString = String.valueOf(totalHrs);  // Convert double to string

                    // Update the checkout time for the check-in entry
                    databaseReference.orderByChild("checkInTime").equalTo(checkInTime)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        snapshot.getRef().child("checkoutTime").setValue(checkoutTime);
                                        // Assuming snapshot is a DataSnapshot or DatabaseReference
                                        snapshot.getRef().child("totalHrs").setValue(totalHrsString);

                                    }
                                    attendanceAdapter.notifyDataSetChanged();

                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.remove(PREFS_KEY_CHECKIN_TIME);
                                    editor.apply();

                                    binding.cardViewCheckIn.setVisibility(View.VISIBLE);
                                    binding.cardViewCheckout.setVisibility(View.GONE);
                                    // Update the "Total Hrs" TextView in the UI
                                    updateTotalHrsTextView(totalHrs);

                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    Toast.makeText(getActivity(), "Failed to update checkout time", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(getActivity(), "No check-in time found", Toast.LENGTH_SHORT).show();
                }
            }
        });


        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                updateClock();
                handler.postDelayed(this, 1000);
                updateDateAndDay();
            }
        });
        return binding.getRoot();
    }

    private void updateTotalHrsTextView(double totalHrs) {
        TextView txtTotalHrs = binding.txtCheckOut.findViewById(R.id.txtTotalHrs);
        if (txtTotalHrs != null) {
            txtTotalHrs.setText("Total hrs: " + String.format("%.2f", totalHrs));
        } else {
            // Handle the case where txtTotalHrs is null (perhaps log an error or take appropriate action)
        }
    }



    private double calculateTotalHours(String checkInTime, String checkoutTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        try {
            Date checkInDate = sdf.parse(checkInTime);
            Date checkoutDate = sdf.parse(checkoutTime);

            // Calculate the difference in milliseconds
            long timeDifference = checkoutDate.getTime() - checkInDate.getTime();

            // Convert milliseconds to hours
            return (double) timeDifference / (60 * 60 * 1000);
        } catch (ParseException e) {
            e.printStackTrace();
            return 0.0; // Return 0.0 in case of an error
        }
    }


 *//*   private void fetchDataFromFirebase() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                employeeList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    AttendanceModel attendance = snapshot.getValue(AttendanceModel.class);
                    attendance.setTotalHrs(String.valueOf(snapshot.child("totalHrs").getValue()));
                    employeeList.add(attendance);
                }
                attendanceAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getActivity(), "Failed to fetch data", Toast.LENGTH_SHORT).show();
            }
        });
    }*//*

    private void fetchDataFromFirebase() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                employeeList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    // Assuming the "id" is a child at the root level, adjust accordingly if it's inside each attendance item
                    String date = snapshot.child("date").getValue(String.class);
                    String day = snapshot.child("day").getValue(String.class);
                    String checkInTime = snapshot.child("checkInTime").getValue(String.class);
                    String checkoutTime = snapshot.child("checkoutTime").getValue(String.class);
                    String totalHrs = snapshot.child("totalHrs").getValue(String.class);

                    AttendanceModel attendance = new AttendanceModel( date, day, checkInTime, checkoutTime, totalHrs);
                    employeeList.add(attendance);
                }
                attendanceAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getActivity(), "Failed to fetch data", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private String getCurrentTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm a", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    private void updateDateAndDay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

        String currentDate = dateFormat.format(new Date());
        String currentDay = dayFormat.format(new Date());

        binding.dateTextView.setText(currentDate);
        binding.dayTextView.setText(currentDay);
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        binding.clockTextView.setText(currentTime);
    }

    private void setUpOnBackPressed() {
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isAdded()) {
                    setEnabled(false);
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    startActivity(intent);
                    getActivity().finish();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);

        getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroy() {
                onBackPressedCallback.remove();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.Toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.FrameLayoutID, new DashboadFragment()).commit();
            }
        });
    }
}*/

package com.suffixit.hrm_suffix.view.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.suffixit.hrm_suffix.Adapter.AttendanceAdapter;
import com.suffixit.hrm_suffix.R;
import com.suffixit.hrm_suffix.databinding.FragmentAttendanceBinding;
import com.suffixit.hrm_suffix.models.AttendanceModel;
import com.suffixit.hrm_suffix.view.Activities.MainActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceFragment extends Fragment {
    private FragmentAttendanceBinding binding;
    private DatabaseReference databaseReference;
    private RecyclerView recyclerView;
    private AttendanceAdapter attendanceAdapter;
    private List<AttendanceModel> employeeList;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_KEY_CHECKIN_TIME = "checkin_time";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAttendanceBinding.inflate(inflater, container, false);

        setUpOnBackPressed();
        recyclerView = binding.recyclerViewId;
        employeeList = new ArrayList<>();

        attendanceAdapter = new AttendanceAdapter(getActivity(), employeeList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(attendanceAdapter);

        sharedPreferences = requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String savedCheckInTime = sharedPreferences.getString(PREFS_KEY_CHECKIN_TIME, "");

        if (!savedCheckInTime.isEmpty()) {
            binding.cardViewCheckIn.setVisibility(View.GONE);
            binding.cardViewCheckout.setVisibility(View.VISIBLE);
            binding.savedCheckInTimeTextView.setText("Check-in time: " + savedCheckInTime);
        }

        databaseReference = FirebaseDatabase.getInstance().getReference().child("Check-ins");
        fetchDataFromFirebase();
        binding.cardViewCheckIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String checkInTime = getCurrentTime();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PREFS_KEY_CHECKIN_TIME, checkInTime);
                editor.apply();

                binding.cardViewCheckIn.setVisibility(View.GONE);
                binding.cardViewCheckout.setVisibility(View.VISIBLE);
                binding.savedCheckInTimeTextView.setText("Check-in time: " + checkInTime);
                String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String currentDay = new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date());
                String checkoutTime = "00.00";
                String totalHrs="00.00";

                AttendanceModel checkIn = new AttendanceModel(currentDate, currentDay, checkInTime, checkoutTime, totalHrs);
                employeeList.add(checkIn);
                attendanceAdapter.notifyDataSetChanged();
                databaseReference.push().setValue(checkIn)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                    Toast.makeText(getActivity(), "Check-in successful", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(getActivity(), "Failed to check in", Toast.LENGTH_SHORT).show();
                            }
                        });
                }
        });

        binding.cardViewCheckout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String checkInTime = sharedPreferences.getString(PREFS_KEY_CHECKIN_TIME, "");

                if (!checkInTime.isEmpty()) {
                    String checkoutTime = getCurrentTime();


                    double totalHrs = calculateTotalHours(checkInTime, checkoutTime);


                    String finalCheckoutTime = checkoutTime;
                    databaseReference.orderByChild("checkInTime").equalTo(checkInTime)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        snapshot.getRef().child("checkoutTime").setValue(finalCheckoutTime);
                                        snapshot.getRef().child("totalHrs").setValue(String.format("%.2f", totalHrs));
                                    }

                                    attendanceAdapter.notifyDataSetChanged();

                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.remove(PREFS_KEY_CHECKIN_TIME);
                                    editor.apply();

                                    binding.cardViewCheckIn.setVisibility(View.VISIBLE);
                                    binding.cardViewCheckout.setVisibility(View.GONE);

                                    // Update the "Total Hrs" TextView in the UI
                                    updateTotalHrsTextView(String.format("%.2f", totalHrs));

                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    Toast.makeText(getActivity(), "Failed to update checkout time", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(getActivity(), "No check-in time found", Toast.LENGTH_SHORT).show();
                }
            }
        });


        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                updateClock();
                handler.postDelayed(this, 1000);
                updateDateAndDay();
            }
        });
        return binding.getRoot();
    }

    private double calculateTotalHours(String checkInTime, String checkoutTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        try {
            Date checkInDate = sdf.parse(checkInTime);
            Date checkoutDate = sdf.parse(checkoutTime);

            long timeDifference = checkoutDate.getTime() - checkInDate.getTime();


            return (double) timeDifference / (60 * 60 * 1000);
        } catch (ParseException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    private void updateTotalHrsTextView(String totalHrs) {
        View view = binding.getRoot();

        if (view != null) {
            TextView txtTotalHrs = view.findViewById(R.id.txtTotalHrs);

            if (txtTotalHrs != null) {
                txtTotalHrs.setText("Total hrs: " + totalHrs);
            } else {
                Toast.makeText(getActivity(), "txtTotalHrs is null", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getActivity(), "Root view is null", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchDataFromFirebase() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                employeeList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    AttendanceModel attendance = snapshot.getValue(AttendanceModel.class);
                    employeeList.add(attendance);
                }
                attendanceAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getActivity(), "Failed to fetch data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getCurrentTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm a", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    private void updateDateAndDay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

        String currentDate = dateFormat.format(new Date());
        String currentDay = dayFormat.format(new Date());

        binding.dateTextView.setText(currentDate);
        binding.dayTextView.setText(currentDay);
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        binding.clockTextView.setText(currentTime);
    }

    private void setUpOnBackPressed() {
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isAdded()) {
                    setEnabled(false);
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    startActivity(intent);
                    getActivity().finish();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
        getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroy() {
                onBackPressedCallback.remove();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.Toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.FrameLayoutID, new DashboadFragment()).commit();
            }
        });
    }
}