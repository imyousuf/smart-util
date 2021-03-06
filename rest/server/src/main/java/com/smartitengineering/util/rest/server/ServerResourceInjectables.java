/*
 * This is a utility project for wide range of applications
 *
 * Copyright (C) 2010  Imran M Yousuf (imyousuf@smartitengineering.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  10-1  USA
 */
package com.smartitengineering.util.rest.server;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.ResourceContext;
import javax.ws.rs.core.UriInfo;

/**
 *
 * @author imyousuf
 */
public class ServerResourceInjectables {

  private UriInfo uriInfo;
  private HttpContext context;
  private ResourceContext resourceContext;

  public HttpContext getContext() {
    return context;
  }

  public void setContext(HttpContext context) {
    this.context = context;
  }

  public ResourceContext getResourceContext() {
    return resourceContext;
  }

  public void setResourceContext(ResourceContext resourceContext) {
    this.resourceContext = resourceContext;
  }

  public UriInfo getUriInfo() {
    return uriInfo;
  }

  public void setUriInfo(UriInfo uriInfo) {
    this.uriInfo = uriInfo;
  }
}
