<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#050505">

    <!-- الشريط العلوي -->
    <LinearLayout
        android:id="@+id/topBarDispersion"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="8dp"
        android:background="#111111"
        app:layout_constraintTop_toTopOf="parent">
        <ImageButton android:id="@+id/btnCloseDispersion" android:layout_width="40dp" android:layout_height="40dp" android:background="?attr/selectableItemBackgroundBorderless" android:src="@android:drawable/ic_menu_close_clear_cancel" app:tint="#FFFFFF" />
        <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="محرك التشتت" android:textColor="#00D4FF" android:textSize="18sp" android:textStyle="bold" android:gravity="center"/>
        <ImageButton android:id="@+id/btnUndoDispersion" android:layout_width="40dp" android:layout_height="40dp" android:background="?attr/selectableItemBackgroundBorderless" android:src="@android:drawable/ic_menu_revert" app:tint="#FFFFFF" />
        <ImageButton android:id="@+id/btnRedoDispersion" android:layout_width="40dp" android:layout_height="40dp" android:background="?attr/selectableItemBackgroundBorderless" android:src="@android:drawable/ic_menu_rotate" app:tint="#FFFFFF" />
        <Button android:id="@+id/btnApplyDispersion" android:layout_width="wrap_content" android:layout_height="36dp" android:text="تطبيق" android:textColor="#000000" android:backgroundTint="#00D4FF" android:textStyle="bold" android:layout_marginStart="8dp"/>
    </LinearLayout>

    <!-- شريط اختيار الوضع (فرشاة / سحب / نص) -->
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabModeDispersion"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="#0A0A0A"
        app:tabIndicatorColor="#00D4FF"
        app:tabSelectedTextColor="#00D4FF"
        app:tabTextColor="#666666"
        app:layout_constraintTop_toBottomOf="@+id/topBarDispersion">
        <com.google.android.material.tabs.TabItem android:text="فرشاة (تلقائي)" />
        <com.google.android.material.tabs.TabItem android:text="سحب يدوي" />
        <com.google.android.material.tabs.TabItem android:text="نص" />
    </com.google.android.material.tabs.TabLayout>

    <!-- منطقة الصورة والرسم -->
    <FrameLayout
        android:id="@+id/imageContainerDispersion"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        app:layout_constraintTop_toBottomOf="@+id/tabModeDispersion"
        app:layout_constraintBottom_toTopOf="@+id/bottomPanelDispersion">

        <ImageView android:id="@+id/ivDispersionImage" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="fitCenter" android:background="#000000" />
        
        <!-- محرك الجزيئات -->
        <com.monolith.SovereignParticleView
            android:id="@+id/particleView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <!-- فرشاة التحديد -->
        <com.monolith.MaskDrawingView
            android:id="@+id/maskDrawingView"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
            
        <Button android:id="@+id/btnPickImageForDispersion" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="اختر صورة للتشتيت" android:textColor="#000000" android:backgroundTint="#00D4FF" android:layout_gravity="center" />
    </FrameLayout>

    <!-- لوحة التحكم السفلية -->
    <LinearLayout
        android:id="@+id/bottomPanelDispersion"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="#111111"
        app:layout_constraintBottom_toBottomOf="parent">

        <!-- أدوات الفرشاة (تظهر فقط في وضع الفرشاة) -->
        <LinearLayout
            android:id="@+id/layoutBrushTools"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center"
            android:padding="8dp">
            <Button android:id="@+id/btnBrushTool" android:layout_width="0dp" android:layout_weight="1" android:layout_height="40dp" android:text="فرشاة" android:backgroundTint="#00D4FF" android:textColor="#000000"/>
            <Button android:id="@+id/btnEraserTool" android:layout_width="0dp" android:layout_weight="1" android:layout_height="40dp" android:layout_marginStart="8dp" android:text="ممحاة" android:backgroundTint="#333333" android:textColor="#FFFFFF"/>
        </LinearLayout>

        <!-- أشرطة التحكم في التأثير -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="شدة التشتت (Dispersion)" android:textColor="#FFFFFF" android:textSize="14sp" />
            <SeekBar android:id="@+id/sbDispersionAmount" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="100" android:progress="50" android:progressTint="#00D4FF" />

            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="حجم الشظايا (Size)" android:textColor="#FFFFFF" android:textSize="14sp" android:layout_marginTop="8dp"/>
            <SeekBar android:id="@+id/sbParticleSize" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="100" android:progress="20" android:progressTint="#00D4FF" />

        </LinearLayout>
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
