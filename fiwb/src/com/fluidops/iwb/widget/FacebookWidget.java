/*
 * Copyright (C) 2008-2013, fluid Operations AG
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.fluidops.iwb.widget;

import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.fluidops.ajax.components.FComponent;
import com.fluidops.iwb.model.ParameterConfigDoc;
import com.fluidops.iwb.model.ParameterConfigDoc.Type;
import com.fluidops.iwb.widget.WidgetEmbeddingError.ErrorType;
import com.fluidops.util.GenUtil;
import com.fluidops.util.StringUtil;

/**
 * Shows the facebook like-box for a valid facebook fan page id
 * or depending on the facebook account given as 'http://facebook.com/..' connect, join or attend badges are displayed
 * @author ango
 */

public class FacebookWidget extends AbstractWidget<FacebookWidget.Config>
{
	/**
	 * logger
	 */
	private static final Logger logger = Logger.getLogger(FacebookWidget.class.getName());



	/**
	 * Facebook Widget config class
	 * 
	 * @author ango
	 */
	public static class Config
	{
		@ParameterConfigDoc(
				desc = "the facebook account (the url of the corresponding facebook page",
				required = true)
		public String facebookAccount;


		@ParameterConfigDoc(
				desc = "The type of the account: a company or a member profile",
				required = false,
				defaultValue = "Member",
				type = Type.DROPDOWN)
		public ProfileType profileType = ProfileType.Member;

	}

	/**
	 * Specify the type of the facebook profile widget. It can be a fan page of a member profile. 
	 * The way the data is retrieved depends on the type.
	 *
	 */
	public static enum ProfileType
	{
		Member,
		Page
	}

	@Override
	public FComponent getComponent(String id)
	{

		Config conf = get();
		
		if(conf == null || StringUtil.isNullOrEmpty(conf.facebookAccount))
            return WidgetEmbeddingError.getErrorLabel(id,
                    ErrorType.MISSING_INPUT_VARIABLE);
		
		return selectFacebookWidget(conf.facebookAccount, id, conf.profileType);

	}

	private FComponent selectFacebookWidget(String profileURL, String id, ProfileType profileType) {

		try {	
             //check if the account is of type fanpage (like 'http://www.facebook.com/SAPSoftware' or 'http://www.facebook.com/pages/FluidOps/102807473121759')
			
			if(profileType == ProfileType.Page )
			{
				Long checkNumber = null;

				try {
					checkNumber=Long.parseLong(profileURL.substring(profileURL.lastIndexOf("/")+1));
				} catch (Exception e1) {
					//the id is not a number but a username, we'll get the id number for it
				}
				String fid =null;
				if(checkNumber==null)
				{
					URL	url = new URL("https://graph.facebook.com/"+profileURL.substring(profileURL.lastIndexOf("/")+1)+"?fields=id");
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");

					if( conn.getResponseCode()==HttpURLConnection.HTTP_OK) 
					{
						String  content = GenUtil.readUrl(conn.getInputStream());
						JSONObject obj = (JSONObject) getJson(content);
						fid = obj.getString("id");
					}else

						throw new RuntimeException("Facebook server didn't return a valid response.");   	

				}else

					fid=profileURL.substring(profileURL.lastIndexOf("/")+1);

				return fanPage(fid, id);
			}

			// check if the account is of type 'group' (like 'http://www.facebook.com/group.php?gid=48548184040')
			if(profileURL.contains("group")&&profileURL.contains("gid"))

			{
				URL	url = new URL("http://graph.facebook.com/"+profileURL.substring(profileURL.lastIndexOf("gid=")+4)+"/picture?type=large");

				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");

				if( conn.getResponseCode()==HttpURLConnection.HTTP_OK) 
				{
					String  pic = conn.getURL().toString();

					return badge("Join the group", pic, profileURL, id);
				} throw new RuntimeException("Facebook server didn't return a valid response.");   		
			}
			//check if it is a profile of a person
			if(profileURL.contains("profile")&&profileURL.contains("id"))

			{
				URL	url = new URL("http://graph.facebook.com/"+profileURL.substring(profileURL.lastIndexOf("id=")+3)+"/picture?type=large");

				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");

				if( conn.getResponseCode()==HttpURLConnection.HTTP_OK) 
				{
					String  pic = conn.getURL().toString();

					return badge("Connect on facebook", pic, profileURL, id);
				} throw new RuntimeException("Facebook server didn't return a valid response.");   		
			}

			//check if the account is of type 'event' (like 'http://www.facebook.com/event.php?eid=159316914109404')
			if(profileURL.contains("event")&&profileURL.contains("eid"))
			{

				URL	url = new URL("http://graph.facebook.com/"+profileURL.substring(profileURL.lastIndexOf("eid=")+4)+"/picture?type=large");

				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");

				if( conn.getResponseCode()==HttpURLConnection.HTTP_OK) 
				{
					String  pic = conn.getURL().toString();

					return badge("Attend the event", pic, profileURL, id);
				}  throw new RuntimeException("Facebook server didn't return a valid response.");   		
			}

			//if the type of the account is not clear, try to find a picture and create a badge

			URL	url = new URL("http://graph.facebook.com/"+profileURL.substring(profileURL.lastIndexOf("/")+1)+"/picture?type=large");

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			if( conn.getResponseCode()==HttpURLConnection.HTTP_OK) 
			{
				String  pic = conn.getURL().toString();

				return badge("Connect on facebook", pic, profileURL, id);
			} throw new RuntimeException("Facebook server didn't return a valid response.");    		
		}

		catch (Exception e) {
			logger.warn(e.getMessage());
			return WidgetEmbeddingError.getErrorLabel(id,
					WidgetEmbeddingError.ErrorType.INVALID_WIDGET_CONFIGURATION,
			"Facebook server didn't return a valid response.");
		}

	}

	private FComponent badge(String message, String pic, String profileURL, String id) 
	{
		final String title = message;
		final String url = profileURL;
		final String image = pic;
		return new FComponent(id)
		{
			@Override
			public String render()
			{  
				return 
				"<center><div style=\"text-align:center;padding-top:5px; background-color:#3B5998;width:200px;border:1px;border-color:#CCCCCC;\">" +
				"<div style=\"padding-top:5px;\" ><a href=\""+url+"\"target=\"_TOP\" style=\"font-family: &quot;lucida grande&quot;,tahoma,verdana,arial,sans-serif; " +
				"font-size: 12px; font-variant: normal; font-style: normal; font-weight: bold; color: #FFFFFF; text-decoration: none;\" " +
				"title=\""+title+"\">"+title+"</a></div><br/><a href=\""+url+"\" target=\"_TOP\" " +
				"title=\"\"><img src=\""+image+"\" width=\"200\" height=\"auto\" " +
				"style=\"border: 0px;\" /></a></div></center>";
			}
		};
	}


	private FComponent fanPage(String facebookID, String id) {

		final String fbID = facebookID;
		if(fbID==null)throw new RuntimeException("coudn't find facebook profile");

		return new FComponent(id)
		{
			@Override
			public String render()
			{  

				return "<center><iframe src='//www.facebook.com/plugins/likebox.php?href=http%3A%2F%2Fwww.facebook.com%2Fpages%2F"+pc.title+"%2F"+fbID+"&amp;" +
				"width=300&amp;colorscheme=light&amp;show_faces=true&amp;stream=true&amp;header=false&amp;height=600' " +
				"scrolling='no' frameborder='0' style='border:none; overflow:visible; " +
				"max-width:100%; height:600px;' allowTransparency='true'></iframe></center>";
			}
		};
	}


	@Override
	public String getTitle()
	{
		return "Facebook";
	}

	@Override
	public Class<?> getConfigClass()
	{
		return FacebookWidget.Config.class;
	}

	public static Object getJson(String content)
	{
		JSONTokener tokener = new JSONTokener(content);
		try
		{
			JSONObject o = new JSONObject(tokener);
			return o;
		}
		catch(Exception e) { logger.error(e.getMessage(), e); }

		try
		{
			JSONArray a = new JSONArray(tokener);
			return a;
		}
		catch(Exception e) { logger.error(e.getMessage(), e); }
		return null;
	}
}
