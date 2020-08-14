/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cloud.etcd;

public class EtcdNodeInformation {
	String module;
	String uuid;
	String targetHost;
	String targetPort;
	String name;

	public EtcdNodeInformation(String module, String uuid, String targetHost, String targetPort, String name) {
		this.module = module;
		this.uuid = uuid;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.name = name;
	}

	public boolean isValid() {
		if (module == null) {
			return false;
		} else if (uuid == null) {
			return false;
		} else if (targetHost == null) {
			return false;
		} else if (targetPort == null) {
			return false;
		} else if (name == null) {
			return false;
		}
		return true;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getTargetHost() {
		return targetHost;
	}

	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}

	public String getTargetPort() {
		return targetPort;
	}

	public void setTargetPort(String targetPort) {
		this.targetPort = targetPort;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((module == null) ? 0 : module.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((targetHost == null) ? 0 : targetHost.hashCode());
		result = prime * result + ((targetPort == null) ? 0 : targetPort.hashCode());
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EtcdNodeInformation other = (EtcdNodeInformation) obj;
		if (module == null) {
			if (other.module != null)
				return false;
		} else if (!module.equals(other.module))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (targetHost == null) {
			if (other.targetHost != null)
				return false;
		} else if (!targetHost.equals(other.targetHost))
			return false;
		if (targetPort == null) {
			if (other.targetPort != null)
				return false;
		} else if (!targetPort.equals(other.targetPort))
			return false;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[EtcdNodeInformation module=" + module + ", uuid=" + uuid + ", targetHost=" + targetHost
				+ ", targetPort=" + targetPort + ", name=" + name + "]";
	}
}
