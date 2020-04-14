extern crate chrono;
use std::net::{TcpListener, TcpStream, IpAddr, Ipv4Addr};
use std::io::{Read, Write};
use std::fs::File;
use std::time::Duration;
use chrono::Utc;

fn main() {
    try_start_server()
}

fn try_start_server() {
    const TCP_PORT: u16 = 4329;
    //const THREAD_POOL_SIZE: u16 = 12;
    let listener = TcpListener::bind((IpAddr::V4(Ipv4Addr::new(0,0,0,0)), TCP_PORT)).unwrap();
    for stream in listener.incoming() {
        match stream {
            Ok(t) => handle_client(t),
            _ => println!("Invalid client?")
        }
    }
}

fn handle_client(mut stream: TcpStream) {
    stream.set_read_timeout(Some(Duration::from_secs(5)));
    stream.set_write_timeout(Some(Duration::from_secs(5)));

    let mut log_info = String::new();

    let date_time = Utc::now().to_rfc3339();

    log_info += &format!("Received from {} at {}\n", stream.peer_addr().unwrap().ip().to_string(), date_time);
    println!("{}", log_info);
    stream.read_to_string(&mut log_info);
    log_info += "\n";

    let mut file = File::create(format!("{}.log", date_time));
    match &mut file {
        Ok(t) => t.write_all(log_info.as_bytes()).unwrap(),
        Err(e) => println!("Error: could not write to file.")
    };
}