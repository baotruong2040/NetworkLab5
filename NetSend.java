import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;

public class NetSend implements Runnable {
    private static final int CONG = 5000;
    private static final int MAX_BYTE = 65507;
    private static final String NHOM = "239.1.1.1";

    private final BufferedReader banPhim = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private NetworkInterface card;
    private InterfaceAddress diaChi;
    private MulticastSocket socket;
    private InetSocketAddress nhom;
    private boolean daJoin;

    public static void main(String[] args) {
        // Windows co the dung encoding khac UTF-8 cho dau ra Console.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        try {
            new NetSend().chay();
        } catch (IOException e) {
            System.err.println("Khong the chay NetSend: " + e.getMessage());
        }
    }

    private void chay() throws IOException {
        if (!chonCard()) {
            return;
        }
        nhom = new InetSocketAddress(InetAddress.getByName(NHOM), CONG);

        // Mot socket nhan ca Unicast, Broadcast va Multicast.
        socket = new MulticastSocket(null);
        Thread luongNhan = null;
        try {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("0.0.0.0", CONG));
            socket.setBroadcast(true);
            socket.setTimeToLive(1);
            if (card.supportsMulticast()) {
                socket.setNetworkInterface(card);
            }

            System.out.println("IP: " + diaChi.getAddress().getHostAddress()
                    + " | Card: " + card.getName() + " | UDP: " + CONG);
            InetAddress broadcast = diaChi.getBroadcast();
            System.out.println("Broadcast: " + (broadcast == null
                    ? "khong co" : broadcast.getHostAddress()));
            System.out.println("Chua tham gia group. Go help de xem huong dan.");

            // Thread chinh nhap lenh; thread nay chi nhan va hien thi tin.
            luongNhan = new Thread(this, "nhan-tin");
            luongNhan.start();

            String lenh;
            while ((lenh = banPhim.readLine()) != null) {
                lenh = lenh.trim();
                if (lenh.equals("exit")) {
                    break;
                }
                try {
                    xuLyLenh(lenh);
                } catch (IOException | IllegalArgumentException e) {
                    System.out.println("Loi: " + e.getMessage());
                }
            }
        } finally {
            // close() danh thuc receive() dang cho va tu huy membership.
            socket.close();
            if (luongNhan != null) {
                try {
                    luongNhan.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("Da dong NetSend.");
        }
    }

    private boolean chonCard() throws IOException {
        ArrayList<NetworkInterface> cacCard = new ArrayList<>();
        ArrayList<InterfaceAddress> cacDiaChi = new ArrayList<>();
        Enumeration<NetworkInterface> ds = NetworkInterface.getNetworkInterfaces();
        while (ds != null && ds.hasMoreElements()) {
            NetworkInterface c = ds.nextElement();
            if (!c.isUp() || c.isLoopback()) {
                continue;
            }
            for (InterfaceAddress dc : c.getInterfaceAddresses()) {
                if (dc.getAddress() instanceof Inet4Address) {
                    cacCard.add(c);
                    cacDiaChi.add(dc);
                }
            }
        }
        if (cacCard.isEmpty()) {
            throw new IOException("Khong tim thay card mang IPv4 dang hoat dong.");
        }
        int so = 0;
        if (cacCard.size() > 1) {
            System.out.println("Chon card LAN/Wi-Fi dung chung voi cac may khac:");
            for (int i = 0; i < cacCard.size(); i++) {
                System.out.println((i + 1) + ". " + cacCard.get(i).getName()
                        + " (" + cacCard.get(i).getDisplayName() + ") - "
                        + cacDiaChi.get(i).getAddress().getHostAddress());
            }
            while (true) {
                System.out.println("Nhap so card (hoac exit):");
                String dong = banPhim.readLine();
                if (dong == null || dong.trim().equals("exit")) {
                    return false;
                }
                try {
                    so = Integer.parseInt(dong.trim()) - 1;
                    if (so >= 0 && so < cacCard.size()) {
                        break;
                    }
                } catch (NumberFormatException e) {
                    // Nhap lai neu khong phai so.
                }
                System.out.println("So card khong hop le.");
            }
        }
        card = cacCard.get(so);
        diaChi = cacDiaChi.get(so);
        return true;
    }

    private void xuLyLenh(String lenh) throws IOException {
        if (lenh.isEmpty()) {
            return;
        }
        if (lenh.equals("help")) {
            huongDan();
        } else if (lenh.equals("join group")) {
            if (daJoin) {
                System.out.println("Da tham gia group truoc do.");
            } else {
                if (!card.supportsMulticast()) {
                    throw new IOException("Card da chon khong ho tro Multicast.");
                }
                socket.joinGroup(nhom, card);
                daJoin = true;
                System.out.println("Da tham gia group " + NHOM);
            }
        } else if (lenh.equals("leave group")) {
            if (!daJoin) {
                System.out.println("Chua tham gia group.");
            } else {
                socket.leaveGroup(nhom, card);
                daJoin = false;
                System.out.println("Da roi group.");
            }
        } else {
            // Gioi han 4 phan de noi dung van giu cac khoang trang ben trong.
            String[] phan = lenh.split("\\s+", 4);
            if (phan.length != 4 || !phan[0].equals("net")
                    || !phan[1].equals("send") || phan[3].isBlank()) {
                System.out.println("Sai cu phap. Go help de xem huong dan.");
                return;
            }
            guiTin(phan[2], phan[3]);
        }
    }

    private void guiTin(String dich, String tinNhan) throws IOException {
        InetAddress ipNhan;
        if (dich.equals("group")) {
            if (!card.supportsMulticast()) {
                throw new IOException("Card da chon khong ho tro Multicast.");
            }
            ipNhan = nhom.getAddress();
        } else if (dich.equals("*")) {
            ipNhan = diaChi.getBroadcast();
            if (ipNhan == null) {
                throw new IOException("Card da chon khong co dia chi Broadcast.");
            }
        } else {
            ipNhan = docIPv4(dich);
        }
        byte[] duLieu = tinNhan.getBytes(StandardCharsets.UTF_8);
        if (duLieu.length > MAX_BYTE) {
            throw new IllegalArgumentException("Tin nhan vuot qua " + MAX_BYTE + " byte UTF-8.");
        }
        DatagramPacket goiTin = new DatagramPacket(duLieu, duLieu.length, ipNhan, CONG);
        socket.send(goiTin);
        // Chi thong bao trang thai; noi dung tin duoc in o nhanTin().
        System.out.println("Da gui UDP den " + dich + " (khong dam bao da nhan).");
    }

    private InetAddress docIPv4(String ip) throws IOException {
        String[] phan = ip.split("\\.", -1);
        if (phan.length != 4) {
            throw new IllegalArgumentException("Can nhap IPv4, vi du 192.168.1.20.");
        }
        byte[] so = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!phan[i].matches("[0-9]{1,3}")) {
                throw new IllegalArgumentException("Dia chi IPv4 khong hop le.");
            }
            int n = Integer.parseInt(phan[i]);
            if (n > 255) {
                throw new IllegalArgumentException("Moi phan IPv4 phai tu 0 den 255.");
            }
            so[i] = (byte) n;
        }
        InetAddress dc = InetAddress.getByAddress(so);
        if (dc.isAnyLocalAddress() || dc.isMulticastAddress()
                || dc.getHostAddress().equals("255.255.255.255")) {
            throw new IllegalArgumentException("Dung IP may nhan; dung group hoac * cho gui nhom.");
        }
        return dc;
    }

    @Override
    public void run() {
        nhanTin();
    }

    private void nhanTin() {
        byte[] duLieu = new byte[MAX_BYTE];
        while (!socket.isClosed()) {
            // Tao lai packet de do dai nhan khong bi gioi han boi goi truoc.
            DatagramPacket goiTin = new DatagramPacket(duLieu, duLieu.length);
            try {
                socket.receive(goiTin);
                if (goiTin.getLength() == 0) {
                    continue;
                }
                String tinNhan = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(goiTin.getData(),
                                goiTin.getOffset(), goiTin.getLength())).toString();
                System.out.println("[" + goiTin.getAddress().getHostAddress() + "] " + tinNhan);
            } catch (CharacterCodingException e) {
                System.out.println("Bo qua goi tin khong dung UTF-8.");
            } catch (SocketException e) {
                if (!socket.isClosed()) {
                    System.out.println("Loi socket nhan: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                System.out.println("Loi nhan tin: " + e.getMessage());
            }
        }
    }

    private void huongDan() {
        System.out.println("net send <IPv4> <tin nhan> : gui den mot may");
        System.out.println("net send group <tin nhan>  : gui den nhom " + NHOM);
        System.out.println("net send * <tin nhan>      : gui Broadcast tren card da chon");
        System.out.println("join group                : tham gia nhom");
        System.out.println("leave group               : roi nhom");
        System.out.println("help                      : xem huong dan");
        System.out.println("exit                      : dong socket va thoat");
    }
}
