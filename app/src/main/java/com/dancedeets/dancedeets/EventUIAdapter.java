package com.dancedeets.dancedeets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.util.List;

/**
 * Created by lambert on 2014/10/02.
 */
public class EventUIAdapter extends BaseAdapter {
    static class ViewBinder {
        NetworkImageView icon;
        TextView title;
        TextView location;
        TextView startTime;
    }
    private LayoutInflater mInflater;
    private List<Event> mEventBundleList;
    private int mResource;

    public EventUIAdapter(Context context, List<Event> eventBundleList, int resource) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mEventBundleList = eventBundleList;
        mResource = resource;

    }
    public int getCount() {
        return mEventBundleList.size();
    }

    @Override
    public Object getItem(int position) {
        return mEventBundleList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position; // TODO: do we want to return the FB ID of the item itself? If we do, override hasStableIds()
    }

    protected void bindView(int position, View view) {
        Event event = (Event)getItem(position);
        ImageLoader thumbnailLoader = VolleySingleton.getInstance(null).getThumbnailLoader();

        ViewBinder viewBinder = (ViewBinder)view.getTag();
        viewBinder.icon.setImageUrl(event.getThumbnailUrl(), thumbnailLoader);
        viewBinder.title.setText(event.getTitle());
        viewBinder.location.setText(event.getLocation());
        viewBinder.startTime.setText(event.getStartTimeString());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView,
                                        ViewGroup parent, int resource) {
        View view;
        if (convertView == null) {
            view = mInflater.inflate(resource, parent, false);
            ViewBinder viewBinder = new ViewBinder();
            viewBinder.icon = (NetworkImageView )view.findViewById(R.id.icon);
            viewBinder.title = (TextView)view.findViewById(R.id.title);
            viewBinder.location = (TextView)view.findViewById(R.id.location);
            viewBinder.startTime = (TextView)view.findViewById(R.id.start_time);
            view.setTag(viewBinder);
        } else {
            view = convertView;
        }

        bindView(position, view);

        return view;
    }

}