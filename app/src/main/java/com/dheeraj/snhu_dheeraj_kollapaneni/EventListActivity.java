package com.dheeraj.snhu_dheeraj_kollapaneni;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class EventListActivity extends AppCompatActivity
        implements EventAdapter.OnEventClickListener,
        NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "EventListActivity";
    private RecyclerView recyclerView;
    private EventAdapter eventAdapter;
    private List<Event> eventList;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private DrawerLayout drawerLayout;
    private FloatingActionButton fabAddEvent;
    private NavigationView navigationView;
    private String currentUserRole = "user"; // default role
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup Drawer and NavigationView
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerViewEvents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventList = new ArrayList<>();

        // Initially, we don’t know if user is admin yet, so pass false.
        // We’ll update it once we fetchUserRole.
        eventAdapter = new EventAdapter(eventList, this, false);
        recyclerView.setAdapter(eventAdapter);

        // Setup FAB
        fabAddEvent = findViewById(R.id.fabAddEvent);
        fabAddEvent.setOnClickListener(v -> {
            Intent intent = new Intent(EventListActivity.this, AddEditEventActivity.class);
            startActivity(intent);
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            fetchUserRole(mAuth.getCurrentUser().getUid());
        } else {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void fetchUserRole(String userId) {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        if (role != null) {
                            currentUserRole = role;
                        }
                        isAdmin = "admin".equalsIgnoreCase(currentUserRole);
                        Log.d(TAG, "Fetched user role: " + currentUserRole);

                        // Update the adapter so it knows if user is admin
                        eventAdapter.setAdmin(isAdmin);

                        // Update navigation menu: hide admin panel if not admin
                        Menu menu = navigationView.getMenu();
                        MenuItem adminItem = menu.findItem(R.id.nav_admin_panel);
                        adminItem.setVisible(isAdmin);

                        // Now that we know the role, fetch the appropriate events
                        fetchEvents();
                    } else {
                        Log.d(TAG, "User document does not exist");
                        fetchEvents(); // fallback to normal user view
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EventListActivity.this, "Failed to fetch user role", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error fetching user role", e);
                    // fallback to normal user view
                    fetchEvents();
                });
    }

    private void fetchEvents() {
        // If admin, show all events. If not, show only approved events.
        if (isAdmin) {
            db.collection("events").addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Toast.makeText(EventListActivity.this, "Error loading events", Toast.LENGTH_SHORT).show();
                    return;
                }
                eventList.clear();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Event event = doc.toObject(Event.class);
                        event.setEventId(doc.getId());
                        eventList.add(event);
                    }
                }
                eventAdapter.notifyDataSetChanged();
            });
        } else {
            // Normal user: only see "approved" events
            db.collection("events")
                    .whereEqualTo("status", "approved")
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            Toast.makeText(EventListActivity.this, "Error loading events", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        eventList.clear();
                        if (snapshots != null) {
                            for (QueryDocumentSnapshot doc : snapshots) {
                                Event event = doc.toObject(Event.class);
                                event.setEventId(doc.getId());
                                eventList.add(event);
                            }
                        }
                        eventAdapter.notifyDataSetChanged();
                    });
        }
    }

    @Override
    public void onEditClick(Event event) {
        // If you want normal users to be able to edit, keep this.
        // If you don't, you can hide the edit button as well for non-admin.
        Intent intent = new Intent(EventListActivity.this, AddEditEventActivity.class);
        intent.putExtra("event_id", event.getEventId());
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(Event event) {
        // Only admins see this button, so we can just delete
        db.collection("events").document(event.getEventId())
                .delete()
                .addOnSuccessListener(aVoid -> fetchEvents())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error deleting event", Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_admin_panel) {
            // Admin only
            startActivity(new Intent(EventListActivity.this, AdminPanelActivity.class));
        } else if (id == R.id.nav_events) {
            // We are already in the event list.
        } else if (id == R.id.nav_sms_alerts) {
            startActivity(new Intent(EventListActivity.this, SmsPermissionActivity.class));
        } else if (id == R.id.nav_logout) {
            mAuth.signOut();
            startActivity(new Intent(EventListActivity.this, LoginActivity.class));
            finish();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}
