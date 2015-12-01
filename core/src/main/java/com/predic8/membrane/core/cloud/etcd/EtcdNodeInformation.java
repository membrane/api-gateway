package com.predic8.membrane.core.cloud.etcd;

public class EtcdNodeInformation {
	String module;
	String uuid;
	String targetHost;
	int targetPort;
	String name;

	public EtcdNodeInformation(String module, String uuid, String targetHost, int targetPort, String name) {
		this.module = module;
		this.uuid = uuid;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.name = name;
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

	public int getTargetPort() {
		return targetPort;
	}

	public void setTargetPort(int targetPort) {
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
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[EtcdNodeInformation module=" + module + ", uuid=" + uuid + ", targetHost=" + targetHost + ", targetPort=" + targetPort + ", name=" + name + "]";
	}
}
