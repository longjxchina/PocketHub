package com.github.mobile.repo;

import static com.madgag.android.listviews.ViewInflator.viewInflatorFor;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.github.mobile.HomeActivity;
import com.github.mobile.HomeActivity.OrgSelectionListener;
import com.github.mobile.R.layout;
import com.github.mobile.R.string;
import com.github.mobile.async.AuthenticatedUserLoader;
import com.github.mobile.persistence.AccountDataManager;
import com.github.mobile.repo.RecentReposHelper.RecentRepos;
import com.github.mobile.ui.ListLoadingFragment;
import com.github.mobile.ui.repo.RepositoryViewActivity;
import com.github.mobile.util.ListViewUtils;
import com.google.inject.Inject;
import com.madgag.android.listviews.ReflectiveHolderFactory;
import com.madgag.android.listviews.ViewHoldingListAdapter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;

/**
 * Fragment to display a list of {@link Repository} instances
 */
public class RepoListFragment extends ListLoadingFragment<Repository> implements OrgSelectionListener {

    private static final String TAG = "RLF";

    private static final String RECENT_REPOS = "recentRepos";

    @Inject
    private AccountDataManager cache;

    private final AtomicReference<User> org = new AtomicReference<User>();

    private RecentReposHelper recentReposHelper;

    private final AtomicReference<RecentRepos> recentReposRef = new AtomicReference<RecentRepos>();

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        recentReposHelper = new RecentReposHelper(activity);
        org.set(((HomeActivity) activity).registerOrgSelectionListener(this));
    }

    @Override
    public void onOrgSelected(User org) {
        User previousOrg = this.org.get();
        int previousOrgId = previousOrg != null ? previousOrg.getId() : -1;
        this.org.set(org);
        // Only hard refresh if view already created and org is changing
        if (getView() != null && previousOrgId != org.getId())
            hideOldContentAndRefresh();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setEmptyText(getString(string.no_repositories));
        ListViewUtils.configure(getActivity(), getListView(), true);

        if (savedInstanceState != null) {
            RecentRepos recentRepos = (RecentRepos) savedInstanceState.getSerializable(RECENT_REPOS);
            if (recentRepos != null)
                recentReposRef.set(recentRepos);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        RecentRepos recentRepos = recentReposRef.get();
        if (recentRepos != null)
            outState.putSerializable(RECENT_REPOS, recentRepos);
    }

    @Override
    public void onListItemClick(ListView list, View v, int position, long id) {
        Repository repo = (Repository) list.getItemAtPosition(position);
        recentReposHelper.add(repo);
        startActivity(RepositoryViewActivity.createIntent(repo));
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();

        recentReposHelper.save();
    }

    @Override
    public Loader<List<Repository>> onCreateLoader(int id, final Bundle args) {
        Log.d(TAG, "Creating loader " + getClass());
        return new AuthenticatedUserLoader<List<Repository>>(getActivity()) {

            public List<Repository> load() {
                User org = RepoListFragment.this.org.get();
                if (org == null)
                    return Collections.emptyList();
                try {
                    Log.d(TAG, "Going to load repos for " + org.getLogin());
                    List<Repository> repos = cache.getRepos(org, isForcedReload(args));
                    RecentRepos recentRepos = recentReposHelper.recentReposFrom(repos, 5);
                    recentReposRef.set(recentRepos);
                    return recentRepos.fullRepoListHeadedByTopRecents;
                } catch (IOException e) {
                    Log.d(TAG, "Error getting repositories", e);
                    showError(e, string.error_repos_load);
                    return Collections.emptyList();
                }
            }
        };
    }

    @Override
    protected ViewHoldingListAdapter<Repository> adapterFor(List<Repository> items) {
        return new ViewHoldingListAdapter<Repository>(items, viewInflatorFor(getActivity(), layout.repo_list_item),
            ReflectiveHolderFactory.reflectiveFactoryFor(RepoViewHolder.class, org, recentReposRef));
    }

}