# REQ-LAN-RECOVERY-001

Version: 1.0; captured: 2026-10-14 (Asia/Shanghai).

## S1. User request (verbatim)

修改taplinksdk在自动重连开启的时候还先报错连接异常再做新端口连接的问题

## S2. Observed log evidence

User attachment: 已粘贴的文本.txt, 2026-10-14 19:30:44.
Relevant event order (summary, not a new requirement): autoReconnect=true;
8445 refused at .313; kernel ERROR at .315; SDK disconnected at .317;
SDK_FAILURE E241 at .322; discovery finds same serial at port 8443 at .510;
address-change reconnect at .517; SDK CONNECT success at .626.

## S3. Existing implementation evidence (not an approved contract)

LanClientKernel.setupConnectionManager.onError unconditionally reports E241 for
handshake failure while performConnect can continue service discovery.
SDK ConnectionManager.connectInternal maps autoReconnect=false to disabled
allowServiceDiscoveryFallback and enableServiceMonitoring.
The suppressed handshake close still updates DISCONNECTED, which is visible to
SDK kernel status synchronization. Therefore suppressing E241 alone is insufficient.

## S4. Approval

Maintainer response after the R1–R6 / AC1–AC6 approval request (verbatim):

开始实现

The current conversation authorizes implementation of the presented scope and cases.
