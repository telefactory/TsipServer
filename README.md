T-SIP Web frontend
==================

Web based interface for managing users and customers and configuring call flows.

License
-------

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.


Prerequisites
-------------

All servers : Ubuntu 16.04.6 LTS

Server A (app server)
- java 1.8
- apache-activemq 5.14.5

Server B (sip server)
- Asterisk 15.2.0

Server C (sbc server)
- Kamailio 4.4.7
- RtpProxy

Development
-----------
Eclipse


Production
----------
service tsip-server start
/usr/lib/apache-activemq-5.14.5/bin/activemq start

service asterisk start

service kamailio start
/etc/init.d/rtpproxy start
