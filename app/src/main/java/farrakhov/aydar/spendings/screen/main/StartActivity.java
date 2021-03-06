package farrakhov.aydar.spendings.screen.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import farrakhov.aydar.spendings.R;
import farrakhov.aydar.spendings.content.CreditCard;
import farrakhov.aydar.spendings.content.Spending;
import farrakhov.aydar.spendings.content.helper.CategoryWithDetails;
import farrakhov.aydar.spendings.content.helper.Period;
import farrakhov.aydar.spendings.screen.category.CategoryActivity;
import farrakhov.aydar.spendings.screen.spending.SpendingActivity;
import farrakhov.aydar.spendings.util.PriceUtil;
import farrakhov.aydar.spendings.util.SMSReader;

import static farrakhov.aydar.spendings.screen.category.CategoryActivity.CATEGORY_ID_ATTR;
import static farrakhov.aydar.spendings.screen.main.EditCreditDialog.CREDIT_ID_ATTR;
import static farrakhov.aydar.spendings.screen.spending.SpendingActivity.SPENDING_ID_ATTR;
import static farrakhov.aydar.spendings.util.PriceUtil.colorize;

public class StartActivity extends AppCompatActivity {

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;

    static Map<Integer, PlaceholderFragment> mFragmentMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.budget));
        setSupportActionBar(toolbar);
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

    }

    @Override
    protected void onResume() {
        super.onResume();
        int pos = mViewPager.getCurrentItem();
        if (mFragmentMap.containsKey(pos)) {
            mFragmentMap.get(pos).init();
        }

    }

    public static class PlaceholderFragment extends Fragment implements MainView,
            SpendingsAdapter.OnItemClickListener,
            CategoryAdapter.OnItemClickListener,
            AddCategoryDialog.AddCategoryDialogListener,
            CreditCardAdapter.OnItemClickListener,
            EditCreditDialog.EditCreditDialogListener {

        private static final String ARG_SECTION_NUMBER = "section_number";

        private MainPresenter mPresenter;

        RecyclerView mSpendingsRecycler;
        RecyclerView mCreditRecyclerView;
        RecyclerView mCategoriesRecyclerView;
        ImageButton mCreateCategoryButton;
        TextView mTotalRest;
        TextView mTotalSpendings;
        TextView mLastSpending;
        TextView mLeftForSpending;
        LinearLayout mCreditCard;
        LinearLayout mCategoriesCard;
        LinearLayout mSpendingsCard;
        Period mPeriod;

        public static final int MY_PERMISSIONS_REQUEST_READ_SMS = 1;

        private SpendingsAdapter mAdapter;
        private CreditCardAdapter mCreditCardAdapter;
        private CategoryAdapter mCategoryAdapter;

        private float mTotalPlaned;

        public PlaceholderFragment() {
        }

        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            if (mFragmentMap == null) {
                mFragmentMap = new HashMap<>();
            }
            mFragmentMap.put(sectionNumber, fragment);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_start, container, false);
            mSpendingsRecycler = (RecyclerView) rootView.findViewById(R.id.recyclerView);
            mCreditRecyclerView = (RecyclerView) rootView.findViewById(R.id.creditRecyclerView);
            mCategoriesRecyclerView = (RecyclerView) rootView.findViewById(R.id.categoriesRecyclerView);
            mCreateCategoryButton = (ImageButton) rootView.findViewById(R.id.add_category);
            mTotalRest = (TextView) rootView.findViewById(R.id.rest_total) ;
            mTotalSpendings = (TextView) rootView.findViewById(R.id.total_spendings);
            mLastSpending = (TextView) rootView.findViewById(R.id.last_spending);
            mCreditCard = (LinearLayout) rootView.findViewById(R.id.animated_credit_card);
            mCategoriesCard = (LinearLayout) rootView.findViewById(R.id.animated_categories_card);
            mSpendingsCard = (LinearLayout) rootView.findViewById(R.id.animated_spendings_card);
            mLeftForSpending = (TextView) rootView.findViewById(R.id.left_for_spending);

            mCreditCard.setOnClickListener(l -> toggleRecycler(mCreditRecyclerView));
            mCategoriesCard.setOnClickListener(l -> toggleRecycler(mCategoriesRecyclerView));
            mSpendingsCard.setOnClickListener(l -> toggleRecycler(mSpendingsRecycler));

            init();

            mCreateCategoryButton.setOnClickListener(i -> {
                new AddCategoryDialog()
                        .setListener(this)
                        .show(getActivity().getFragmentManager(), "dialog");
            });
            return rootView;
        }

        private void init() {
            initSpendings();
            initCreditCards();
            initCategories();

            mPeriod = Period.getByNum(getArguments().getInt(ARG_SECTION_NUMBER));
            mPresenter = new MainPresenter(this, mPeriod);
            mPresenter.init(getActivity());
        }

        @Override
        public void onResume() {
            super.onResume();
            init();
        }

        private void toggleRecycler(RecyclerView view) {
            if (View.VISIBLE == view.getVisibility()) {
                view.setVisibility(View.GONE);
                return;
            }
            view.setVisibility(View.VISIBLE);
        }

        private void initCategories() {
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity().getApplicationContext(),
                    LinearLayoutManager.VERTICAL, false);
            mCategoriesRecyclerView.setLayoutManager(layoutManager);
            mCategoryAdapter = new CategoryAdapter(this, getContext());
            mCategoriesRecyclerView.setAdapter(mCategoryAdapter);
        }

        private void initCreditCards() {
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity().getApplicationContext(),
                    LinearLayoutManager.VERTICAL, false);
            mCreditRecyclerView.setLayoutManager(layoutManager);
            mCreditCardAdapter = new CreditCardAdapter(this);
            mCreditRecyclerView.setAdapter(mCreditCardAdapter);
        }

        private void initSpendings() {
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity().getApplicationContext(),
                    LinearLayoutManager.VERTICAL, false);
            mSpendingsRecycler.setLayoutManager(layoutManager);
            mAdapter = new SpendingsAdapter(this);
            mSpendingsRecycler.setAdapter(mAdapter);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode,
                                               String permissions[], int[] grantResults) {
            switch (requestCode) {
                case MY_PERMISSIONS_REQUEST_READ_SMS: {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                        SMSReader.getNewSms(getActivity());
                    }
                }

            }
        }

        @Override
        public void requestSmsReadPermission() {
            if (ContextCompat.checkSelfPermission(getActivity(),
                    Manifest.permission.READ_SMS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.READ_SMS},
                        MY_PERMISSIONS_REQUEST_READ_SMS);

            } else {
                SMSReader.getNewSms(getActivity());
            }
        }

        @Override
        public void showSpendings(List<Spending> spendingList) {
            Float total = 0f;
            for (Spending spending : spendingList) {
                total += spending.getSum();
            }
            if (spendingList.size() > 0) {
                Spending last = spendingList.get(0);
                mLastSpending.setText(String.format("%s - %s", PriceUtil.format(last.getSum()),
                        last.getShop().getDisplayName()));
            } else {
                mLastSpending.setText("-");
            }

            mTotalSpendings.setText(PriceUtil.format(total));
            mLeftForSpending.setText(PriceUtil.format(mTotalPlaned - total));
            colorize(mLeftForSpending, mTotalPlaned - total, getContext());
            mAdapter.changeDataSet(spendingList);
        }

        @Override
        public void showCreditCards(List<CreditCard> creditCards) {
            Float total = 0f;
            for (CreditCard creditCard : creditCards) {
                total += creditCard.getCredit();
            }
            mTotalRest.setText(PriceUtil.format(total));
            mCreditCardAdapter.changeDataSet(creditCards);
        }

        @Override
        public void showCategories(List<CategoryWithDetails> categories) {
            mTotalPlaned = 0;
            for (CategoryWithDetails category : categories) {
                if (Period.MONTH.equals(mPeriod) || !category.isMonthly()) {
                    mTotalPlaned += category.getPlanned();
                }
            }
            Collections.sort(categories, (c1, c2) -> c2.getTotal() - c1.getTotal() > 0 ? 1 : -1);
            mCategoryAdapter.changeDataSet(categories);
        }

        @Override
        public void onItemClick(Spending item) {
            Intent intent = new Intent(getActivity(), SpendingActivity.class);
            intent.putExtra(SPENDING_ID_ATTR, item.getId());
            startActivity(intent);
        }

        @Override
        public void onItemClick(CategoryWithDetails item) {
            Intent intent = new Intent(getActivity(), CategoryActivity.class);
            intent.putExtra(CATEGORY_ID_ATTR, item.getId());
            startActivity(intent);

        }

        @Override
        public void add(String name) {
            mPresenter.addCategory(name);
        }

        @Override
        public void onItemClick(CreditCard item) {
            Bundle bundle = new Bundle();
            bundle.putLong(CREDIT_ID_ATTR, item.getId());
            EditCreditDialog dialog = new EditCreditDialog();
            dialog.setListener(this);
            dialog.setArguments(bundle);
            dialog.show(getActivity().getFragmentManager(), "dialog");
        }

        @Override
        public void changeRest(CreditCard creditCard, Float rest) {
            mPresenter.changeRest(creditCard, rest);
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Месяц";
                case 1:
                    return "Неделя";
                case 2:
                    return "День";
            }
            return null;
        }
    }
}
