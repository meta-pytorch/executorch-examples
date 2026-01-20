/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class LogsAdapter(
    context: Context,
    resource: Int
) : ArrayAdapter<AppLog>(context, resource) {

    private class ViewHolder {
        lateinit var logTextView: TextView
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val viewHolder: ViewHolder
        val view: View

        val logMessage = getItem(position)?.getFormattedLog() ?: ""

        if (convertView == null || convertView.tag == null) {
            viewHolder = ViewHolder()
            view = LayoutInflater.from(context).inflate(R.layout.logs_message, parent, false)
            viewHolder.logTextView = view.requireViewById(R.id.logsTextView)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = convertView.tag as ViewHolder
        }

        viewHolder.logTextView.text = logMessage
        return view
    }
}
