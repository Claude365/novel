package com.chhuang.novel;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.*;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.chhuang.novel.data.Article;
import com.chhuang.novel.data.GBKRequest;
import com.chhuang.novel.data.dao.ArticleDataHelper;
import com.chhuang.novel.data.dao.ArticleInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DirectoryActivity extends Activity
        implements SwipeRefreshLayout.OnRefreshListener, LoaderManager.LoaderCallbacks<Cursor> {
    public static final String  TAG                  = DirectoryActivity.class.getName();
    public static final Pattern ARTICLE_HREF_PATTERN = Pattern.compile("/5_5133/(\\d+).html", Pattern.CASE_INSENSITIVE);
    public static final String  BASE_URL             = "http://www.biquge.com/5_5133/";
    public static final int     ARTICLE_LOADER       = 0;
    @InjectView(R.id.layout_titles)
    SwipeRefreshLayout layoutTitles;
    @InjectView(R.id.list_titles)
    ListView           listViewTitles;
    private RequestQueue          requestQueue;
    private SimpleCursorAdapter   articleSimpleCursorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_directory);

        init();
        getLoaderManager().initLoader(ARTICLE_LOADER, null, this);
    }

    private void init() {
        ButterKnife.inject(this);

        layoutTitles.setOnRefreshListener(this);
        layoutTitles.setColorScheme(android.R.color.holo_blue_bright,
                                    android.R.color.holo_green_light,
                                    android.R.color.holo_orange_light,
                                    android.R.color.holo_red_light);
        requestQueue = Volley.newRequestQueue(this);
        articleSimpleCursorAdapter = new ArticleCursorAdapter(this,
                                                              R.layout.title_item,
                                                              null,
                                                              new String[0],
                                                              new int[0],
                                                              0);
        listViewTitles.setAdapter(articleSimpleCursorAdapter);
        listViewTitles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = ((SimpleCursorAdapter) listViewTitles.getAdapter()).getCursor();
                cursor.moveToPosition(position);
                Article article = ArticleDataHelper.fromCursor(cursor);
                Intent intent = new Intent(DirectoryActivity.this, ArticleActivity.class).putExtra("article", article);
                startActivity(intent);
            }
        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case ARTICLE_LOADER:
                return new CursorLoader(this,
                                        ArticleDataHelper.ARTICLE_CONTENT_URI,
                                        ArticleInfo.PROJECTIONS,
                                        null,
                                        null,
                                        ArticleInfo._ID);
            default:
                return null;
        }

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        articleSimpleCursorAdapter.changeCursor(data);
        if (data == null || data.getCount() == 0) {
            onRefresh();
        }
    }

    @Override
    public void onRefresh() {
        layoutTitles.setRefreshing(true);

        requestQueue.add(new GBKRequest(BASE_URL, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

                ArrayList<Article> articles = new ArrayList<Article>();
                Document document = Jsoup.parse(response);
                Elements dds = document.select("dd").select("a");
                for (Element a : dds) {
                    String href = a.attr("href");
                    Matcher matcher = ARTICLE_HREF_PATTERN.matcher(href);
                    if (matcher.matches()) {
                        int id = Integer.parseInt(matcher.group(1));
                        String title = a.text();
                        String url = BASE_URL + id + ".html";
                        Article article = new Article(id, title, url);
                        articles.add(article);
                    }
                }

                ArticleDataHelper.getInstance(AppContext.getContext()).bulkInsert(articles);

                layoutTitles.setRefreshing(false);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                AppContext.showToast(DirectoryActivity.this, "刷新失败，请稍后重试", Toast.LENGTH_LONG);
                layoutTitles.setRefreshing(false);
            }
        }));
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        articleSimpleCursorAdapter.changeCursor(null);
    }

    private class ArticleCursorAdapter extends SimpleCursorAdapter {
        private ArticleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.title_item, null);
            }
            TextView chapterNumber = (TextView) view.findViewById(R.id.text_chapter);
            TextView chapterTitle = (TextView) view.findViewById(R.id.text_title);
            ImageView star = (ImageView) view.findViewById(R.id.image_status);
            ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progress_article);
            Article article = ArticleDataHelper.fromCursor(cursor);
            if (TextUtils.isEmpty(article.getContent())) {
                star.setImageState(new int[]{android.R.attr.state_pressed}, false);
            } else {
                star.setImageState(new int[]{android.R.attr.state_checked, android.R.attr.state_pressed}, false);
            }
            final int progress = (int) (100 * article.getPercentage());
            chapterNumber.setText(String.format("%04d", article.getId()));
            chapterTitle.setText(MessageFormat.format("{0}({1}%)", article.getTitle(), progress));
            progressBar.setProgress(progress);

        }
    }
}