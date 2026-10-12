# Net Send — bài tập Java UDP

Chương trình Console cho mỗi máy vừa gửi vừa nhận tin, không cần máy chủ. Dùng JDK 17 trở lên, không cần Maven, Gradle hoặc thư viện ngoài.

## 1. Ba cách gửi tin

| Loại | Đích gửi | Máy nhận |
| --- | --- | --- |
| Unicast | IPv4 của một máy | Máy có IP đó, đang chạy NetSend |
| Multicast | Nhóm cố định `239.1.1.1` | Các máy đã nhập `join group` |
| Broadcast | Broadcast của card mạng được chọn | Các máy trong cùng mạng Broadcast, đang chạy NetSend |

Tất cả dùng cổng UDP **5000**. UDP không xác nhận giao hàng: gửi thành công chỉ có nghĩa hệ điều hành đã nhận yêu cầu gửi. Gói có thể bị mất, đảo thứ tự hoặc bị mạng nhân đôi.

## 2. Cấu trúc

```text
multicast/
    NetSend.java
    README.md
```

Các file `.class` xuất hiện sau khi biên dịch là sản phẩm biên dịch. Source chỉ có một class.

## 3. Biên dịch và chạy

Mở Terminal tại thư mục chứa `NetSend.java`. Kiểm tra:

```text
java -version
javac -version
```

Cả hai cần phiên bản 17 trở lên. Nếu không tìm thấy `javac`, cài JDK và thêm thư mục `bin` của JDK vào PATH.

### Windows PowerShell

Đặt encoding UTF-8 trước khi chạy để nhập và hiển thị tiếng Việt:

```powershell
chcp 65001
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
javac -encoding UTF-8 NetSend.java
java NetSend
```

### Linux Terminal

Dùng Terminal và locale UTF-8; kiểm tra bằng `locale`.

```bash
javac -encoding UTF-8 NetSend.java
java NetSend
```

Trên JDK mới hơn, có thể kiểm tra tương thích Java 17 bằng:

```bash
javac -encoding UTF-8 --release 17 NetSend.java
```

`--release 17` kiểm tra API và định dạng bytecode Java 17; không thay thế chạy thử trên JDK 17 thực tế.

## 4. Chuẩn bị nhiều máy

1. Kết nối A, B, C vào cùng LAN/Wi-Fi có cho phép các máy liên lạc.
2. Trên Windows chạy `ipconfig`; trên Linux chạy `ip addr`. Lấy IPv4 của Ethernet/Wi-Fi đang dùng, tránh IP VPN hoặc card ảo.
3. Sao chép hai file, biên dịch và mở một phiên NetSend trên mỗi máy.
4. Khi chỉ có một địa chỉ IPv4 phù hợp, chương trình tự chọn. Nếu có nhiều, nhập số tương ứng card LAN/Wi-Fi. Nếu một card có nhiều IPv4, mỗi địa chỉ là một lựa chọn.
5. Kiểm tra dòng IP, tên card, Broadcast và cổng khi chương trình bắt đầu.
6. Cho phép nhận UDP 5000 qua firewall. Chỉ thay đổi firewall trên máy bạn có quyền quản lý.

Ví dụ mở PowerShell **Run as administrator** trên Windows, dùng mạng được đặt là Private:

```powershell
New-NetFirewallRule -DisplayName "NetSend UDP 5000" -Direction Inbound -Action Allow -Protocol UDP -LocalPort 5000 -Profile Private
```

Nếu Windows hỏi quyền truy cập mạng của Java, cho phép trên mạng Private đang dùng. Nếu có quy tắc chặn outbound, cần cho phép gửi UDP 5000 đến LAN và `239.1.1.1`.

Trên Linux dùng UFW, nếu UFW đang hoạt động:

```bash
sudo ufw allow 5000/udp
```

Nếu dùng công cụ firewall khác, tạo quy tắc tương đương; không cần tắt toàn bộ firewall. Chương trình không tự sửa cấu hình mạng hay firewall.

## 5. Các lệnh

Nhập các lệnh dưới đây **bên trong NetSend**, không nhập ở dấu nhắc PowerShell/bash:

```text
net send 192.168.1.20 Xin chao ban
join group
net send group Xin chao ca nhom
leave group
net send * Xin chao tat ca
help
exit
```

Lệnh dùng chữ thường. Nội dung không cần đặt trong dấu nháy, có thể chứa khoảng trắng và tiếng Việt có dấu. Khoảng trắng ở hai đầu dòng được bỏ đi; khoảng trắng bên trong nội dung được giữ lại.

Các máy chưa join vẫn nhận Unicast/Broadcast. Không cần join để gửi Multicast. Máy gửi đã join có thể nhận chính tin Multicast của mình; chương trình giữ thiết lập loopback mặc định. Broadcast tự gửi có xuất hiện hay không phụ thuộc hệ điều hành và mạng.

`exit` hoặc kết thúc đầu vào đóng socket và dừng Thread nhận. Mỗi máy chỉ chạy **một phiên** trên cổng 5000. Nếu bị báo cổng đã sử dụng, đóng phiên cũ hoặc kiểm tra ứng dụng khác đang giữ cổng.

## 6. Giải thích code

- `chonCard()`: duyệt `NetworkInterface`, lấy các IPv4 trên card đang hoạt động và không phải loopback. Lưu cả `InterfaceAddress` để lấy đúng Broadcast theo subnet, không hardcode `192.168.1.255`.
- `chay()`: tạo `MulticastSocket(null)`, tắt chia sẻ cổng trước khi bind `0.0.0.0:5000`. Bind wildcard cho phép nhận Unicast và Broadcast; join bổ sung nhận Multicast trên card được chọn.
- `MulticastSocket` kế thừa `DatagramSocket`, nên vẫn dùng `send`, `receive` và `DatagramPacket`. Một socket nhận chung tránh hai socket tranh cổng hoặc cùng nhận một gói. `setBroadcast(true)` cho phép gửi Broadcast; `setTimeToLive(1)` giới hạn phạm vi Multicast.
- `NetSend implements Runnable`: `new Thread(this, "nhan-tin")` chạy `run()` rồi `nhanTin()`. Thread chính tiếp tục đọc bàn phím bằng `BufferedReader`. Việc đang chờ `receive()` không khóa nhập lệnh. Dòng nhận tin có thể xen vào dòng đang nhập; Console cơ bản không vẽ lại dòng nhập.
- `xuLyLenh()`: xử lý các lệnh; tách tối đa bốn phần khi gửi để giữ phần nội dung. `daJoin` chỉ được Thread chính sử dụng và chỉ đổi sau khi join/leave thành công.
- `guiTin()`: chọn địa chỉ đích, chuyển nội dung sang byte UTF-8, tạo `DatagramPacket`, gọi `send`. Broadcast chỉ gửi một lần đến địa chỉ của lựa chọn hiện tại. Unicast đi theo bảng định tuyến của hệ điều hành; chọn card chủ yếu điều khiển Multicast/Broadcast.
- `docIPv4()`: kiểm tra bốn phần số từ 0–255, không tra DNS; không nhận hostname hoặc IPv6. Địa chỉ Multicast phải dùng lệnh `group`; Broadcast nên dùng `*`.
- `nhanTin()`: lấy IP nguồn từ `getAddress()`, giải mã đúng số byte nhận được. Tạo packet mới mỗi vòng để gói ngắn trước đó không làm cắt gói dài tiếp theo. Gói rỗng bị bỏ qua; UTF-8 lỗi được thông báo rồi tiếp tục nhận. `main()` đặt cả stdout/stderr thành UTF-8 để tránh encoding mặc định khác nhau trên Windows.
- Giới hạn nội dung là **65.507 byte UTF-8**, không phải số ký tự. Đây là giới hạn payload UDP IPv4; tin dài dễ bị phân mảnh và mất gói, nên bài tập dùng tin ngắn.
- `finally`: đóng socket cả khi lỗi hoặc hết đầu vào. `close()` đánh thức `receive()`; Thread nhận thấy socket đã đóng thì thoát, Thread chính đợi bằng `join()`.

Không thêm ID tin nhắn, JSON hay cơ chế xác nhận. Một gói hợp lệ được in một lần tại Thread nhận; không in lại nội dung tại nơi gửi. Cách này tránh lặp do cấu hình ứng dụng, nhưng không cam kết loại bỏ gói bị nhân đôi bởi mạng. Nội dung nhận là văn bản UTF-8 thuần, không có cơ chế xác thực người gửi.

## 7. Ví dụ Console trên ba máy

**Đây là ví dụ minh họa, không phải log kiểm thử nhiều máy.** Giả sử A = `192.168.1.10`, B = `192.168.1.20`, C = `192.168.1.30`.

Máy A nhập:

```text
net send 192.168.1.20 Xin chao ban
Da gui UDP den 192.168.1.20 (khong dam bao da nhan).
join group
Da tham gia group 239.1.1.1
```

Máy B nhận rồi tham gia nhóm:

```text
[192.168.1.10] Xin chao ban
join group
Da tham gia group 239.1.1.1
```

Máy A nhập `net send group Xin chao ca nhom`. A và B có thể hiển thị:

```text
[192.168.1.10] Xin chao ca nhom
```

C chưa join nên không nhận tin nhóm. Sau đó A nhập `net send * Xin chao tat ca`; B và C hiển thị:

```text
[192.168.1.10] Xin chao tat ca
```

B nhập `leave group`. Khi A gửi một tin nhóm mới sau đó, B không còn nhận tin mới của nhóm, nhưng vẫn nhận Unicast/Broadcast. Gói đã trên đường hoặc trong bộ đệm có thể xuất hiện ngay sau khi leave.

## 8. Kiểm thử nhiều máy

Mỗi bài dùng nội dung khác nhau để dễ đếm bản nhận. Chờ chương trình báo join/leave thành công trước khi gửi tin mới.

| Test | Thao tác | Kết quả cần kiểm tra |
| --- | --- | --- |
| 1 | A: `net send 192.168.1.20 Test1` | B in `[192.168.1.10] Test1` một lần; C không nhận |
| 2 | A/B join; C chưa join; A: `net send group Test2` | B nhận một lần; C không nhận; A có thể nhận bản loopback |
| 3 | A: `net send * Test3` | B và C đều nhận một lần |
| 4 | B leave, chờ các gói trước đó xử lý xong; A gửi `net send group Test4` | B không nhận Test4; A vẫn nhận nếu còn join |
| 5 | A/B gửi Unicast qua lại trong khi nhập `help` | Cả hai vẫn nhận; Console không bị treo |
| 6 | Nhập `abc`, `net send`, `net send 999.1.1.1 Hello`, `net send group` | Thông báo lỗi; lệnh hợp lệ tiếp theo vẫn chạy |
| 7 | A gửi `net send 192.168.1.20 Xin chào, bạn khỏe không?` | B hiển thị tiếng Việt đúng |

Thử thêm join hai lần, leave hai lần, `exit`, IP sai và cổng bị chiếm. Khi đối chiếu kết quả, dùng IP thực tế của máy thay cho các IP ví dụ.

## 9. Giới hạn mạng và xử lý sự cố

- Cùng SSID chưa chắc cùng mạng Broadcast hoặc được phép liên lạc. Guest Wi-Fi, VLAN, AP/client isolation và một số hotspot có thể chặn giao tiếp giữa các máy.
- Router/AP có thể chặn hoặc lọc Multicast/Broadcast; Multicast còn phụ thuộc việc quản lý nhóm trên thiết bị mạng. Chọn đúng interface không thể vượt qua các hạn chế này.
- Nếu Unicast chạy nhưng Broadcast/Multicast không chạy, kiểm tra firewall, cấu hình AP, subnet và thử mạng LAN có dây hoặc Wi-Fi không bật isolation.
- Nếu card không hỗ trợ Multicast, chương trình báo lỗi khi join/gửi group. Nếu không có địa chỉ Broadcast, lệnh `*` báo lỗi, không dùng địa chỉ dự phòng một cách âm thầm.
- Chọn card Wi-Fi/Ethernet thay vì VPN/VM khi có nhiều lựa chọn. Một lần chạy chỉ Broadcast trên card đã chọn.
- Không có hứa hẹn nhận tin khi máy đích chưa chạy chương trình, sai cổng, firewall chặn hoặc mạng mất gói.

## 10. Kết quả kiểm tra trong môi trường phát triển

Kiểm tra ngày **12/10/2026**, trên Windows, JDK **21.0.12**, IP local `172.20.10.3`:

| Kiểm tra đã chạy | Kết quả thực tế |
| --- | --- |
| Biên dịch với `--release 17`, `-Xlint:all` | Thành công bằng API trình biên dịch JDK; không cảnh báo; bytecode major version 61 (Java 17) |
| Khởi động, chọn card, tính Broadcast | Nhận đúng IPv4 hiện tại và Broadcast `172.20.10.15` theo subnet |
| Unicast đến `127.0.0.1` | Nhận một lần |
| Nhập/gửi/nhận tiếng Việt có dấu | Nội dung UTF-8 đúng |
| Gói rỗng và UTF-8 lỗi, sau đó gói hợp lệ | Bỏ qua gói lỗi; tiếp tục nhận |
| Gói hơn 4 KB sau gói ngắn | Nhận đầy đủ, không bị cắt theo chiều dài gói trước |
| Tin vượt 65.507 byte | Bị từ chối; chương trình tiếp tục chạy |
| Lệnh sai, IP sai, `help`, khoảng trắng trong tin | Xử lý đúng; vẫn dùng được Console |
| Join/leave lặp lại | Thông báo đúng; Unicast tiếp tục hoạt động sau leave |
| Nhận năm gói trong khi xử lý `help` | Nhận đủ, mỗi gói in một lần |
| Phiên thứ hai trên cổng 5000 | Báo lỗi cổng bị chiếm |
| `exit`, hết đầu vào, khởi động lại | Đóng Thread/socket; giải phóng cổng |
| Gửi Multicast và Broadcast | API gửi không báo lỗi; không nhận được bản quay lại local |

Lệnh `javac` thông thường bị môi trường kiểm tra chặn ghi file `.class`. Để kiểm tra source, đã dùng `JavaCompiler` của JDK 21 với đầu ra trong bộ nhớ và tùy chọn `--release 17`; bytecode được ghi vào thư mục tạm bằng công cụ kiểm thử rồi chạy bằng `java`. Công cụ này không thuộc project và không cần dùng khi chạy bài tập trên máy thông thường.

Một phép thử Multicast độc lập bằng Python trên cùng máy cũng không nhận được gói trong 2 giây dù bật loopback. Vì vậy **chưa xác nhận nhận Multicast/Broadcast thành công**, cũng chưa xác nhận khả năng chặn tin nhóm sau leave bằng đường truyền thực tế. Chưa xác định nguyên nhân ở firewall, mạng hoặc môi trường kiểm tra; không tự thay đổi các cấu hình đó.

**Chưa chạy trên JDK 17 thực tế, Linux hoặc 2–3 máy.** Bảy bài kiểm thử nhiều máy ở trên là hướng dẫn cần thực hiện, không phải kết quả đã đạt. Python và công cụ trình biên dịch trong bộ nhớ chỉ được dùng để kiểm tra local, không phải phụ thuộc của NetSend.
